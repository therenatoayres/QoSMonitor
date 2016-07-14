package eu.arrowhead.core.qos.monitor.type;

import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.exception.MissingParameterException;
import eu.arrowhead.common.model.ArrowheadSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.arrowhead.common.model.messages.QoSMonitorAddRule;
import eu.arrowhead.common.model.messages.QoSMonitorLog;
import eu.arrowhead.core.qos.monitor.event.SLAVerificationParameter;
import eu.arrowhead.core.qos.monitor.event.SLAVerificationResponse;
import eu.arrowhead.core.qos.monitor.database.MonitorLog;
import eu.arrowhead.core.qos.monitor.database.MonitorRule;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FTTSE implements Monitor {

    public static final Logger LOG = Logger.getLogger(FTTSE.class.getName());

    private static enum Key {

        BANDWIDTH("bandwidth"), RESPONSETIME("responseTime"), DELAY("delay");

        private final String name;

        private Key(String name) {
            this.name = name;
        }
    }

    public FTTSE() {
    }

    @Override
    public MonitorRule filterRuleMessage(QoSMonitorAddRule message) {
        ArrowheadSystem provider = message.getProvider();
        ArrowheadSystem consumer = message.getConsumer();

        return new MonitorRule(message.getType(),
                provider.getSystemName(), provider.getSystemGroup(),
                consumer.getSystemName(), consumer.getSystemGroup(),
                filterParameters(message.getParameters()), message.isSoftRealTime());
    }

    @Override
    public MonitorLog filterLogMessage(QoSMonitorLog message) {
        return new MonitorLog(message.getType(), message.getTimestamp(), filterParameters(message.getParameters()));
    }

    @Override
    public SLAVerificationResponse verifyQoS(MonitorRule rule, MonitorLog... logs) {
        int nLogs = logs.length;
        if (nLogs == 0) {
            //FIXME Exception
            return null;
        }

        if (nLogs > 1) {
            //FIXME softRealTime
            return doSoftRealTime(rule.getParameters(), Arrays.asList(logs));
        }

        //FIXME Hard-real Time
        return doHardRealTime(rule.getParameters(), logs[0].getParameters());
    }

    private Map<String, Double> filterParameters(Map<String, String> params) {
        Map<String, Double> parameters = new HashMap<>();

        Key[] keys = Key.values();

        for (Key key : keys) {
            String name = key.name;
            if (!(params.containsKey(name))) {
                String exMsg = "Parameter missing: " + name;
                LOG.log(Level.SEVERE, exMsg);
                throw new MissingParameterException(exMsg);
            }
            try {
                parameters.put(name, Double.valueOf(params.get(name)));
            } catch (NumberFormatException ex) {
                throw new InvalidParameterException("Value of parameter "
                        + name + " is not parsable. Please make sure "
                        + "that no invalid characters are present");
            }
        }

        return parameters;
    }

    private SLAVerificationResponse doHardRealTime(Map<String, Double> rule, Map<String, Double> log) {
        SLAVerificationResponse response = new SLAVerificationResponse();

        Key[] keys = Key.values();

        for (Key key : keys) {
            Double requestedValue = rule.get(key.name);
            Double loggedValue = log.get(key.name);
            switch (key) {
                case BANDWIDTH:
                    if (loggedValue < requestedValue) {
                        response.addParameter(new SLAVerificationParameter(key.name, requestedValue, loggedValue));
                    }
                    break;
                case RESPONSETIME:
                case DELAY:
                    if (loggedValue > requestedValue) {
                        response.addParameter(new SLAVerificationParameter(key.name, requestedValue, loggedValue));
                    }
                    break;
                default:
                    break;
            }
        }

        return response;
    }

    private SLAVerificationResponse doSoftRealTime(Map<String, Double> rule, List<MonitorLog> logs) {
        SLAVerificationResponse response = new SLAVerificationResponse();

        Double bandwidthMean = 0.0;
        Double responseTimeMean = 0.0;
        Double delayMean = 0.0;
        Double nLogs = Double.valueOf(logs.size());

        for (MonitorLog log : logs) {
            Map<String, Double> temp = log.getParameters();
            bandwidthMean += temp.get(Key.BANDWIDTH.name);
            responseTimeMean += temp.get(Key.RESPONSETIME.name);
            delayMean += temp.get(Key.DELAY.name);
        }

        bandwidthMean /= nLogs;
        responseTimeMean /= nLogs;
        delayMean /= nLogs;

        Key[] keys = Key.values();

        for (Key key : keys) {
            Double requestedValue = rule.get(key.name);
            switch (key) {
                case BANDWIDTH:
                    if (bandwidthMean < requestedValue) {
                        response.addParameter(new SLAVerificationParameter(key.name, requestedValue, bandwidthMean));
                    }
                    break;
                case RESPONSETIME:
                    if (responseTimeMean > requestedValue) {
                        response.addParameter(new SLAVerificationParameter(key.name, requestedValue, responseTimeMean));
                    }
                    break;
                case DELAY:
                    if (delayMean > requestedValue) {
                        response.addParameter(new SLAVerificationParameter(key.name, requestedValue, responseTimeMean));
                    }
                    break;
                default:
                    break;
            }
        }

        return response;
    }

}