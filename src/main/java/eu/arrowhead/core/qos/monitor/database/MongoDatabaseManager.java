package eu.arrowhead.core.qos.monitor.database;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteConcernException;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import eu.arrowhead.common.model.ArrowheadSystem;
import eu.arrowhead.core.qos.monitor.database.provider.MonitorLogCodecProvider;
import eu.arrowhead.core.qos.monitor.database.provider.MonitorRuleCodecProvider;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 * The MongoDatabaseManager.
 *
 * This class is responsible for all the actions regarding MongoDB.
 *
 * @author Renato Ayres
 */
public class MongoDatabaseManager {

    private static MongoDatabaseManager instance;
    private static MongoClient client;
    private static MongoDatabase database;
    private static MongoCollection<MonitorRule> rules;
    private static CodecRegistry logCodecRegistry;
    private static CodecRegistry ruleCodecRegistry;
    private Properties props;
    private static final Logger LOG = Logger.getLogger(MongoDatabaseManager.class.getName());

    /**
     * Returns the Singleton instance of MongoDatabaseManager
     *
     * @return Singleton instance
     */
    public static synchronized MongoDatabaseManager getInstance() {
        if (instance == null) {
            initInstance();
        }
        return instance;
    }

    /**
     * Creates a new instance, initializing the Client and the Database
     * instances as well.
     */
    private MongoDatabaseManager() {
        if (client == null) {
            initClient();
        }
        if (database == null) {
            initDatabase();
        }
        initCodecRegistries();
    }

    /**
     * Starts the MongoDatabaseManager
     */
    public static void startManager() {
        initInstance();
    }

    /**
     * Restarts the MongoDatabaseManager with a new properties file.
     *
     */
    public static void restartManager() {
        stopManager();
        initInstance();
    }

    /**
     * Closes the MongoClient connection with MongoDB.
     */
    public static void stopManager() {
        if (client != null) {
            rules = null;
            database = null;
            logCodecRegistry = null;
            ruleCodecRegistry = null;
            client.close();
            client = null;
            instance = null;
        }
    }

    /**
     * Gets the properties file named 'mongodb.properties'.
     *
     * @return the Properties from properties file 'mongodb.properties'
     */
    private synchronized Properties getProps() {
        try {
            if (props == null) {
                props = new Properties();
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("mongodb.properties");
                if (inputStream != null) {
                    props.load(inputStream);
                    inputStream.close();
                } else {
                    throw new FileNotFoundException("Properties file 'mongodb.properties' not found in the classpath");
                }
            }
        } catch (FileNotFoundException ex) {
            LOG.log(Level.SEVERE, ex.getMessage());
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getMessage());
        }
        return props;
    }

    private static void initInstance() {
        if (instance == null) {
            instance = new MongoDatabaseManager();
        }
    }

    /**
     * Initializes the MongoDBClient instance. A connection string is provided
     * in a properties file.
     */
    private void initClient() {
        MongoClientURI uri;

        uri = new MongoClientURI(getProps().getProperty("connectionString"));

        client = new MongoClient(uri);
    }

    /**
     * Initializes the MongoDatabase instance. A database name is provided in a
     * properties file.
     */
    private void initDatabase() {
        database = getClient().getDatabase(getProps().getProperty("database"));
    }

    /**
     * Initializes the codec registries for the MonitorLog class and the
     * MonitorRule class.
     */
    private void initCodecRegistries() {
        initLogCodecRegistry();
        initRuleCodecRegistry();
    }

    /**
     * Initializes the CodecRegistry for the MonitorLog class.
     */
    private static void initLogCodecRegistry() {
        logCodecRegistry = CodecRegistries.fromRegistries(MongoClient.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(new MonitorLogCodecProvider()));
    }

    /**
     * Initializes the CodecRegistry for the MonitorRule class.
     */
    private static void initRuleCodecRegistry() {
        ruleCodecRegistry = CodecRegistries.fromRegistries(MongoClient.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(new MonitorRuleCodecProvider()));
    }

    /**
     * Gets the client for access to the MongoDB instance.
     *
     * @return the client for access to MongoDB
     */
    private MongoClient getClient() {
        if (client == null) {
            initClient();
        }
        return client;
    }

    /**
     * Gets the database access instance.
     *
     * @return the database access instance
     */
    private MongoDatabase getDatabase() {
        if (database == null) {
            initDatabase();
        }
        return database;
    }

    /**
     * Gets the codec registry for the MonitorLog class.
     *
     * @return the codec registry
     */
    private CodecRegistry getLogCodecRegistry() {
        if (logCodecRegistry == null) {
            initLogCodecRegistry();
        }
        return logCodecRegistry;
    }

    /**
     * Gets the codec registry for the MonitorRule class.
     *
     * @return the codec registry
     */
    private CodecRegistry getRuleCodecRegistry() {
        if (ruleCodecRegistry == null) {
            initRuleCodecRegistry();
        }
        return ruleCodecRegistry;
    }

    /**
     * Gets the Rule collection from the MongoDatabase instance.
     *
     * @return the Rule collection from MongoDatabase
     */
    private MongoCollection<MonitorRule> getRuleCollection() {

        if (rules == null) {
            rules = getDatabase().getCollection(MongoDBNames.RULES_TABLE, MonitorRule.class)
                    .withCodecRegistry(getRuleCodecRegistry())
                    .withWriteConcern(WriteConcern.MAJORITY)
                    .withReadConcern(ReadConcern.MAJORITY);
        }
        return rules;
    }

    /**
     * Gets a Log collection from the MongoDatabase instance, identified by the
     * given parameters.
     *
     * @param providerSystemName the provider system name
     * @param providerSystemGroup the provider system group
     * @param consumerSystemName the consumer system name
     * @param consumerSystemGroup the consumer system group
     * @return the Log collection from MongoDatabase
     */
    private MongoCollection<MonitorLog> getLogCollection(
            String providerSystemName, String providerSystemGroup,
            String consumerSystemName, String consumerSystemGroup) {

        String name = providerSystemName + providerSystemGroup + consumerSystemName + consumerSystemGroup;

        MongoCollection< MonitorLog> logs = getDatabase().getCollection(name, MonitorLog.class)
                .withCodecRegistry(getLogCodecRegistry())
                .withWriteConcern(WriteConcern.MAJORITY)
                .withReadConcern(ReadConcern.MAJORITY);

        return logs;
    }

    /**
     * Deletes a collection identified by the system name and system group from
     * the given parameters.
     *
     * @param provider the service provider
     * @param consumer the service consumer
     * @return Always returns true. May suffer some changes in the future
     */
    public boolean deleteCollection(ArrowheadSystem provider, ArrowheadSystem consumer) {
        return deleteCollection(provider.getSystemName(), provider.getSystemGroup(),
                consumer.getSystemName(), consumer.getSystemGroup());
    }

    /**
     * Deletes a collection identified by the system name and system group from
     * the service provider and the service consumer in the given parameters.
     *
     * @param providerSystemName the provider system name
     * @param providerSystemGroup the provider system group
     * @param consumerSystemName the consumer system name
     * @param consumerSystemGroup the consumer system group
     * @return Always returns true. May suffer some changes in the future
     */
    public boolean deleteCollection(String providerSystemName, String providerSystemGroup,
            String consumerSystemName, String consumerSystemGroup) {

        MongoCollection<MonitorLog> logs = getLogCollection(providerSystemName, providerSystemGroup,
                consumerSystemName, consumerSystemGroup);

        logs.drop();

        LOG.log(Level.INFO, "{0}{1}{2}{3} droped.", new String[]{providerSystemName, providerSystemGroup, consumerSystemName, consumerSystemGroup});

        return true;
    }

    /**
     * Inserts a new Rule into the rules collection of MongoDB.
     *
     * @param rule the rule to insert
     * @throws MongoWriteException if the write failed due some other failure
     * specific to the insert command
     * @throws MongoWriteConcernException if the write failed due being unable
     * to fulfil the write concern
     * @throws MongoException if the write failed due some other failure
     */
    public void insertRule(MonitorRule rule) throws MongoWriteException, MongoWriteConcernException, MongoException {
        getRuleCollection().insertOne(rule);
    }

    /**
     * Find a rule with the given provider, and consumer.
     *
     * @param provider the provider
     * @param consumer the consumer
     * @return the wanted rule. If no rule matched the given parameters, then
     * null is returned
     */
    public MonitorRule findRule(ArrowheadSystem provider, ArrowheadSystem consumer) {
        return findRule(provider.getSystemName(), provider.getSystemGroup(),
                consumer.getSystemName(), consumer.getSystemGroup());
    }

    /**
     * Find a rule with the given provider system definition, provider system
     * group, consumer system definition, and consumer system group.
     *
     * @param providerSystemName the provider system name
     * @param providerSystemGroup the provider system group
     * @param consumerSystemName the consumer system name
     * @param consumerSystemGroup the consumer system group
     * @return the wanted rule. If no rule matched the given parameters, then
     * null is returned
     */
    public MonitorRule findRule(String providerSystemName, String providerSystemGroup,
            String consumerSystemName, String consumerSystemGroup) {

        Bson filter = createRuleFilter(providerSystemName, providerSystemGroup,
                consumerSystemName, consumerSystemGroup);

        MonitorRule rule = getRuleCollection().find(
                filter,
                MonitorRule.class).first();

        return rule;

    }

    /**
     * Checks if a rule exists in the MongoDatabase instance. The rule is
     * identified by the system definition and system group information in the
     * given parameters. Uses the Rule collection.
     *
     * @param provider the service provider
     * @param consumer the service consumer
     * @return true if the rule exists, false if not
     */
    public boolean existsRule(ArrowheadSystem provider, ArrowheadSystem consumer) {

        Bson filter = createRuleFilter(provider.getSystemName(),
                provider.getSystemGroup(),
                consumer.getSystemName(),
                consumer.getSystemGroup());

        MonitorRule rule = getRuleCollection().find(
                filter,
                MonitorRule.class).first();

        return !(rule == null);
    }

    /**
     * Checks if a rule exists in the MongoDatabase instance and replaces it.
     * The rule is identified by the system definition and system group
     * information from the given rule.If it doesn't exist, creates it. Uses the
     * Rule collection.
     *
     * @param rule the rule
     * @return Alwyas returns true. May suffer some changes in the future
     */
    public boolean replaceRule(MonitorRule rule) {

        deleteRule(rule.getProviderSystemName(), rule.getProviderSystemGroup(),
                rule.getConsumerSystemName(), rule.getConsumerSystemGroup());

        insertRule(rule);

        return true;
    }

    /**
     * Checks if a rule exists in the MongoDatabase instance and deletes it. The
     * rule is identified by the system definition and system group information
     * in the given parameters. Uses the Rule collection.
     *
     * @param provider the service provider
     * @param consumer the service consumer
     * @return Always returns true. May suffer some changes in the future
     */
    public boolean deleteRule(ArrowheadSystem provider, ArrowheadSystem consumer) {
        return deleteRule(provider.getSystemName(), provider.getSystemGroup(),
                consumer.getSystemName(), consumer.getSystemGroup());
    }

    /**
     * Checks if a rule exists in the MongoDatabase instance and deletes it. The
     * rule is identified by the system name and system group information in the
     * given parameters. Uses the Rule collection.
     *
     * @param providerSystemName the provider system name
     * @param providerSystemGroup the provider system group
     * @param consumerSystemName the consumer system name
     * @param consumerSystemGroup the consumer system group
     * @return Always returns true. May suffer some changes in the future
     */
    public boolean deleteRule(String providerSystemName, String providerSystemGroup,
            String consumerSystemName, String consumerSystemGroup) {

        Bson filter = createRuleFilter(providerSystemName, providerSystemGroup,
                consumerSystemName, consumerSystemGroup);

        getRuleCollection().findOneAndDelete(filter);

        deleteCollection(providerSystemName, providerSystemGroup,
                consumerSystemName, consumerSystemGroup);

        return true;
    }

    /**
     * Inserts a new Log into a MongoDB collection defined by the given
     * parameters.
     *
     * @param log the log to insert
     * @param provider the service provider
     * @param consumer the service consumer
     * @throws MongoWriteException if the write failed due some other failure
     * specific to the insert command
     * @throws MongoWriteConcernException if the write failed due being unable
     * to fulfil the write concern
     * @throws MongoException if the write failed due some other failure
     */
    public void insertLog(MonitorLog log, ArrowheadSystem provider, ArrowheadSystem consumer)
            throws MongoWriteException, MongoWriteConcernException, MongoException {

        MongoCollection<MonitorLog> logs = getLogCollection(
                provider.getSystemName(), provider.getSystemGroup(),
                consumer.getSystemName(), consumer.getSystemGroup());

        logs.insertOne(log);
    }

    /**
     * Gets the last N logs, as defined by the rule
     *
     * @param rule
     * @return
     */
    public MonitorLog[] getLastNLogs(MonitorRule rule) {
        MongoCollection<MonitorLog> logs = getLogCollection(
                rule.getProviderSystemName(), rule.getProviderSystemGroup(),
                rule.getConsumerSystemName(), rule.getConsumerSystemGroup());

        MongoCursor<MonitorLog> sorted = logs.find().sort(Sorts.descending(MongoDBNames.TIMESTAMP)).iterator();

        MonitorLog[] result = new MonitorLog[10];

        //FIXME rule.getSoftRealTimeNValues
        int i = 0;
        while (i != 10) {
            result[i] = sorted.next();
            i--;
        }

        return result;
    }

    /**
     * Inserts a new document into a given collection with a given class type.
     *
     * @param <T> the type of class to use
     * @param coll the name of the collection
     * @param type the type of object
     * @param obj the object to save
     * @return true of false about the insertion operation
     */
    public <T> boolean insert(String coll, Class<T> type, T obj) {
        MongoCollection<T> collection = getDatabase().getCollection(coll, type);

        try {
            collection.insertOne(obj);
        } catch (MongoException ex) {
            //FIXME Multicatch
            return false;
        }
        return true;
    }

    /**
     * Creates a new Bson filter used to find a specific rule identified by the
     * given parameters. Uses the Rule collection.
     *
     * @param providerSystemName the provider system definition
     * @param providerSystemGroup the provider system group
     * @param consumerSystemName the consumer system definition
     * @param consumerSystemGroup the consumer system group
     * @return bson filter built with the given parameters
     */
    private Bson createRuleFilter(String providerSystemName, String providerSystemGroup, String consumerSystemName, String consumerSystemGroup) {
        return Filters.and(
                Filters.and(
                        Filters.eq(MongoDBNames.PROVIDER_SYSTEM_NAME, providerSystemName),
                        Filters.eq(MongoDBNames.PROVIDER_SYSTEM_GROUP, providerSystemGroup)),
                Filters.and(
                        Filters.eq(MongoDBNames.CONSUMER_SYSTEM_NAME, consumerSystemName),
                        Filters.eq(MongoDBNames.CONSUMER_SYSTEM_GROUP, consumerSystemGroup)
                )
        );
    }
}