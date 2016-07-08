package eu.arrowhead.common.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import eu.arrowhead.qos.monitor.database.MongoDatabaseManager;
import eu.arrowhead.qos.monitor.event.EventProducerConfig;
import eu.arrowhead.qos.monitor.event.ProducerRegistry;
import eu.arrowhead.qos.monitor.register.ServiceRegister;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServletContextClass implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(ServletContextClass.class.getName());
    private static final String MONITOR_REGISTRY_PACKAGE = "eu.arrowhead.qos.monitor.type.";
    private static List<String> registered = new ArrayList();

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        System.out.println("[Arrowhead Core] Servlet deployed.");

        System.out.println("Working Directory = "
                + System.getProperty("user.dir"));

        //Register QoSMonitor service in service registry
        List<String> registries = getServiceRegistry();

        for (String registry : registries) {
            try {
                ServiceRegister register = getMonitorClass(registry);
                register.registerQoSMonitorService();
                registered.add(registry);
            } catch (ClassNotFoundException ex) {
                String excMessage = "Not registered in registry " + registry + ". "
                        + "Registry class " + registry + " not found. Make "
                        + "sure you have the right registry class for your "
                        + "situation and that it's available in this version "
                        + "and/or not misspelled.";
                LOG.log(Level.SEVERE, excMessage);
//                throw new RuntimeException(excMessage);
            } catch (InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(ServletContextClass.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        // [PT] Starting MongoDB, loading EventProducer configurations and registering in EventHandler
        EventProducerConfig.loadConfigurations();
        new ProducerRegistry().registerAsProducer();

        MongoDatabaseManager.startManager();

        LOG.info("[Arrowhead Core] Servlet redeployed.");

    }

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        System.out.println("[Arrowhead Core] Servlet destroyed.");

        // [PT] Stoping MongoDB
        MongoDatabaseManager.stopManager();

        //TODO unregister from EventHandler
        
        for (String registry : registered) {
            try {
                ServiceRegister register = getMonitorClass(registry);
                register.unregisterQoSMonitorService();
            } catch (ClassNotFoundException ex) {
                String excMessage = "Registry class " + registry + " not found. Make "
                        + "sure you have the right registry class for your "
                        + "situation and that it's available in this version "
                        + "and/or not misspelled.";
                LOG.log(Level.SEVERE, excMessage);
                throw new RuntimeException(excMessage);
            } catch (InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(ServletContextClass.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Gets a list of ServiceRegistry to register the QoSMonitor service.
     *
     * @return list of ServiceRegistry
     */
    private List<String> getServiceRegistry() {
        Properties props = null;
        String[] registries;
        try {
            props = new Properties();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("serviceregistry.properties");
            if (inputStream != null) {
                props.load(inputStream);
                inputStream.close();
            } else {
                String exMsg = "Properties file 'serviceregistry.properties' not found in the classpath";
                LOG.log(Level.SEVERE, exMsg);
                throw new FileNotFoundException(exMsg);
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, ex.getMessage());
            throw new RuntimeException(ex.getMessage());
        }
        registries = (String[]) props.get("registry.option");
        if (registries.length == 0) {
            String exMsg = "No ServiceRegistry values found in registry.option of serviceregistry.properties file.";
            LOG.log(Level.SEVERE, exMsg);
            throw new RuntimeException(exMsg);
        }
        return Arrays.asList(registries);
    }

    /**
     * Returns an initialized instance of class, finding it by it's name.
     *
     * @param name name of the class to instantiate
     * @return a Monitor implementation
     * @throws ClassNotFoundException thrown when trying to initialize a class
     * that cannot be found. This may be due to the type in the message being
     * wrong or mistyped
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public ServiceRegister getMonitorClass(String name)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<?> cl;

        cl = Class.forName(MONITOR_REGISTRY_PACKAGE + name);

        ServiceRegister monitor = (ServiceRegister) cl.newInstance();

        return monitor;
    }

}
