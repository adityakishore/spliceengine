package com.splicemachine.derby.utils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.db.iapi.error.PublicAPI;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.ResultColumnDescriptor;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.impl.jdbc.Util;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import com.splicemachine.db.jdbc.InternalDriver;

import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.util.Pair;

import com.google.common.io.Closeables;
import com.splicemachine.hbase.jmx.JMXUtils;
import com.splicemachine.pipeline.Exceptions;

/**
 * Common static utility functions for subclasses which provide
 * implementation logic for utility system procedures,
 * which are associated with public static Java methods.
 * For example, many of our administrative system procedures
 * fetch their information from JMX beans, and common methods
 * are provided here to do so.
 * 
 * @see SpliceAdmin
 * @see TimestampAdmin
 */
public abstract class BaseAdminProcedures {

	// WARNING: do not add just any old logic here! This class is only intended
	// for static methods needed to support the back end system procedures.
	// Just because 'admin' is in the name does not mean anything you deem to be
	// related to system admin belongs here. We don't want this to be another
	// dumping around for utility functions.

    protected static List<Pair<String,String>> getServerNames(Collection<ServerName> serverInfo) {
        List<Pair<String,String>> names = new ArrayList<Pair<String,String>>(serverInfo.size());
        for (ServerName sname : serverInfo) {
            names.add(Pair.newPair(sname.getHostname(),sname.getHostAndPort()));
        }
        return names;
    }

	protected static void throwNullArgError(Object value) {
	    throw new IllegalArgumentException(String.format("Required argument %s is null.", value));	
	}
	
    protected static interface JMXServerOperation {
        void operate(List<Pair<String, JMXConnector>> jmxConnector) throws MalformedObjectNameException, IOException, SQLException;
    }

    /**
     * Get the JMX connections for the region servers.
     *
     * @param serverNames
     * @return
     * @throws IOException
     */
    protected static List<Pair<String, JMXConnector>> getConnections(List<ServerName> serverNames) throws IOException {
    	return JMXUtils.getMBeanServerConnections(getServerNames(serverNames));
    }

    /**
     * Execute (or "operate") the JMX operation on the region servers using the specified JMX connections.
     *
     * @param operation
     * @param connections
     *
     * @throws SQLException
     */
    protected static void operateWithExistingConnections(JMXServerOperation operation, List<Pair<String, JMXConnector>> connections) throws SQLException {
    	if (operation == null) throwNullArgError("operation");
    	if (connections == null) throwNullArgError("connections");
        try {
            operation.operate(connections);
        } catch (MalformedObjectNameException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e));
        } catch (IOException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e));
        }
    }

    /**
     * Execute (or "operate") the JMX operation on the region servers.
     * JMX connections will be created and closed for each operation on each region server.
     *
     * @param operation
     * @param serverNames
     *
     * @throws SQLException
     */
    protected static void operate(JMXServerOperation operation, List<ServerName> serverNames) throws SQLException {
    	if (operation == null) throwNullArgError("operation");
        List<Pair<String, JMXConnector>> connections = null;
        try {
            connections = getConnections(serverNames);
            operation.operate(connections);
        } catch (MalformedObjectNameException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e));
        } catch (IOException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e));
        } finally {
            if (connections != null) {
            	close(connections);
            }
        }
    }

    /**
     * Close all JMX connections.
     * 
     * @param connections
     */
    protected static void close(List<Pair<String, JMXConnector>> connections) {
        if (connections != null) {
            for (Pair<String, JMXConnector> connectorPair : connections) {
                Closeables.closeQuietly(connectorPair.getSecond());
            }
        }
    }

    protected static void operate(JMXServerOperation operation) throws SQLException {
    	if (operation == null) throwNullArgError("operation");
        List<ServerName> regionServers = SpliceUtils.getServers();
        operate(operation, regionServers);
    }

    protected static void operateOnMaster(JMXServerOperation operation) throws SQLException {
        if (operation == null) throwNullArgError("operation");

        ServerName masterServer = SpliceUtils.getMasterServer();

        String serverName = masterServer.getHostname();
        String port = SpliceConstants.config.get("hbase.master.jmx.port","10101");
        try {
            JMXServiceURL url = new JMXServiceURL(String.format("service:jmx:rmi://%1$s/jndi/rmi://%1$s:%2$s/jmxrmi",serverName,port));
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
            operation.operate(Arrays.asList(Pair.newPair(serverName,jmxc)));
        } catch (IOException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e));
        } catch (MalformedObjectNameException e) {
            throw PublicAPI.wrapStandardException(Exceptions.parseException(e));
        }
    }

    public static Connection getDefaultConn() throws SQLException {
        InternalDriver id = InternalDriver.activeDriver();
        if (id != null) {
            Connection conn = id.connect("jdbc:default:connection", null);
            if (conn != null)
                return conn;
        }
        throw Util.noCurrentConnection();
    }

    public static ResultSet executeStatement(StringBuilder sb) throws SQLException {
        ResultSet result = null;
        Connection connection = getDefaultConn();
        try {
            PreparedStatement ps = connection.prepareStatement(sb.toString());
            result = ps.executeQuery();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException(sb.toString(), e);
        } finally {
            connection.close();
        }
        return result;
    }

    protected static ExecRow buildExecRow(ResultColumnDescriptor[] columns) throws SQLException {
        ExecRow template = new ValueRow(columns.length);
        try {
            DataValueDescriptor[] rowArray = new DataValueDescriptor[columns.length];
            for(int i=0;i<columns.length;i++){
                rowArray[i] = columns[i].getType().getNull();
            }
            template.setRowArray(rowArray);
        } catch (StandardException e) {
            throw PublicAPI.wrapStandardException(e);
        }
        return template;
    }

}
