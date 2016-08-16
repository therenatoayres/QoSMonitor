/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.arrowhead.core.qos.monitor.type.presentation;

import eu.arrowhead.core.qos.monitor.database.MonitorLog;
import eu.arrowhead.core.qos.monitor.event.model.Event;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 *
 * @author ID0084D
 */
public class PresentationData {

    private final Queue<MonitorLog> logs;
    private final Queue<Event> events;

    public PresentationData() {
        logs = new ArrayDeque<>();
        events = new ArrayDeque<>();
    }

    public Queue<MonitorLog> getLogs() {
        return logs;
    }

    public Queue<Event> getEvents() {
        return events;
    }

}