/**
 * Copyright 2015 Telefonica Investigación y Desarrollo, S.A.U
 *
 * This file is part of fiware-cygnus (FI-WARE project).
 *
 * fiware-cygnus is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * fiware-cygnus is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with fiware-cygnus. If not, see
 * http://www.gnu.org/licenses/.
 *
 * For those usages not covered by the GNU Affero General Public License please contact with iot_support at tid dot es
 */
package com.telefonica.iot.cygnus.backends.postgresql;

import com.telefonica.iot.cygnus.errors.CygnusBadContextData;
import com.telefonica.iot.cygnus.errors.CygnusPersistenceError;
import com.telefonica.iot.cygnus.errors.CygnusRuntimeError;
import com.telefonica.iot.cygnus.log.CygnusLogger;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;

/**
 *
 * @author javimolla
 * 
 *         PostgreSQL related operations (database and table creation, context
 *         data insertion) when dealing with a PostgreSQL persistence backend.
 */
public class PostgreSQLBackendImpl implements PostgreSQLBackend {
    private static final String DRIVER_NAME = "org.postgresql.Driver";
    private PostgreSQLDriver driver;
    private static final CygnusLogger LOGGER = new CygnusLogger(PostgreSQLBackendImpl.class);

    /**
     * Constructor.
     * 
     * @param postgresqlHost
     * @param postgresqlPort
     * @param postgresqlUsername
     * @param postgresqlPassword
     */
    public PostgreSQLBackendImpl(String postgresqlHost, String postgresqlPort, String postgresqlUsername,
                    String postgresqlPassword) {
        driver = new PostgreSQLDriver(postgresqlHost, postgresqlPort, postgresqlUsername, postgresqlPassword);
    } // PostgreSQLBackendImpl

    /**
     * Sets the PostgreSQL driver. It is protected since it is only used by the
     * tests.
     * 
     * @param driver
     *            The PostgreSQL driver to be set.
     */
    protected void setDriver(PostgreSQLDriver driver) {
        this.driver = driver;
    } // setDriver

    protected PostgreSQLDriver getDriver() {
        return driver;
    } // getDriver

    /**
     * Creates a database, given its name, if not exists.
     * 
     * @param dbName
     * @throws Exception
     */
    @Override
    public void createDatabase(String dbName) throws Exception {
        Statement stmt = null;

        // get a connection to an empty database
        Connection con = driver.getConnection("");

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "create database \"" + dbName + "\"";
            LOGGER.debug("Executing PostgreSQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                throw new CygnusRuntimeError(e.getMessage());
            }
        } // try catch

        closePostgreSQLObjects(con, stmt);
    } // createDatabase

    /**
     * Creates a schema, given its name, if not exists in the given database.
     * 
     * @param schemaName
     * @param tableName
     * @throws Exception
     */
    @Override
    public void createSchema(String dbName, String schemaName) throws Exception {
        Statement stmt = null;

        // get a connection to the given database
        Connection con = driver.getConnection(dbName);

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "create schema \"" + schemaName + "\"";
            LOGGER.debug("Executing PostgreSQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                throw new CygnusRuntimeError(e.getMessage());
            }
        } // try catch

        closePostgreSQLObjects(con, stmt);
    }
    
    /**
     * Creates a table, given its name, if not exists in the given database and schema.
     * 
     * @param dbName
     * @param schemaName
     * @param tableName
     * @throws Exception
     */
    @Override
    public void createTable(String dbName, String schemaName, String tableName, String typedFieldNames) throws Exception {
        Statement stmt = null;

        // get a connection to the given database
        Connection con = driver.getConnection(dbName);

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "create table \"" + schemaName + "\".\"" + tableName + "\" " + typedFieldNames;
            LOGGER.debug("Executing PostgreSQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                throw new CygnusRuntimeError(e.getMessage());
            }
        } // try catch

        closePostgreSQLObjects(con, stmt);
    } // createTable


    @Override
    public void insertContextData(String dbName, String schemaName, String tableName, String fieldNames, String fieldValues)
                    throws Exception {
        Statement stmt = null;

        // get a connection to the given database
        Connection con = driver.getConnection(dbName);

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "insert into \"" + schemaName + "\".\"" + tableName + "\" " + fieldNames + " values " + fieldValues;
            LOGGER.debug("Executing PostgreSQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (SQLTimeoutException e) {
            throw new CygnusPersistenceError(e.getMessage());
        } catch (SQLException e) {
            throw new CygnusBadContextData(e.getMessage());
        } // try catch
    } // insertContextData

    /**
     * Close all the PostgreSQL objects previously opened by doCreateTable and
     * doQuery.
     * 
     * @param con
     * @param stmt
     * @return True if the PostgreSQL objects have been closed, false otherwise.
     */
    private void closePostgreSQLObjects(Connection con, Statement stmt) throws Exception {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                throw new CygnusRuntimeError("The Hive connection could not be closed. Details=" + e.getMessage());
            } // try catch
        } // if

        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                throw new CygnusRuntimeError("The Hive statement could not be closed. Details=" + e.getMessage());
            } // try catch
        } // if
    } // closePostgreSQLObjects

    /**
     * This code has been extracted from PostgreSQLBackendImpl.getConnection()
     * for testing purposes. By extracting it into a class then it can be
     * mocked.
     */
    protected class PostgreSQLDriver {

        private final HashMap<String, Connection> connections;
        private final String postgresqlHost;
        private final String postgresqlPort;
        private final String postgresqlUsername;
        private final String postgresqlPassword;

        /**
         * Constructor.
         * 
         * @param postgresqlHost
         * @param postgresqlPort
         * @param postgresqlUsername
         * @param postgresqlPassword
         */
        public PostgreSQLDriver(String postgresqlHost, String postgresqlPort, String postgresqlUsername,
                        String postgresqlPassword) {
            connections = new HashMap<String, Connection>();
            this.postgresqlHost = postgresqlHost;
            this.postgresqlPort = postgresqlPort;
            this.postgresqlUsername = postgresqlUsername;
            this.postgresqlPassword = postgresqlPassword;
        } // PostgreSQLDriver

        /**
         * Gets a connection to the PostgreSQL server.
         * 
         * @param dbName
         * @return
         * @throws Exception
         */
        public Connection getConnection(String dbName) throws Exception {
            try {
                // FIXME: the number of cached connections should be limited to
                // a certain number; with such a limit
                // number, if a new connection is needed, the oldest one is
                // closed
                Connection con = connections.get(dbName);

                if (con == null || !con.isValid(0)) {
                    if (con != null) {
                        con.close();
                    } // if

                    con = createConnection(dbName);
                    connections.put(dbName, con);
                } // if

                return con;
            } catch (ClassNotFoundException e) {
                throw new CygnusPersistenceError(e.getMessage());
            } catch (SQLException e) {
                throw new CygnusPersistenceError(e.getMessage());
            } // try catch
        } // getConnection

        /**
         * Gets if a connection is created for the given database. It is
         * protected since it is only used in the tests.
         * 
         * @param dbName
         * @return True if the connection exists, false other wise
         */
        protected boolean isConnectionCreated(String dbName) {
            return connections.containsKey(dbName);
        } // isConnectionCreated

        /**
         * Gets the number of connections created.
         * 
         * @return The number of connections created
         */
        protected int numConnectionsCreated() {
            return connections.size();
        } // numConnectionsCreated

        /**
         * Creates a PostgreSQL connection.
         * 
         * @param host
         * @param port
         * @param dbName
         * @param user
         * @param password
         * @return A PostgreSQL connection
         * @throws Exception
         */
        private Connection createConnection(String dbName) throws Exception {
            // dynamically load the PostgreSQL JDBC driver
            Class.forName(DRIVER_NAME);

            // return a connection based on the PostgreSQL JDBC driver
            LOGGER.debug("Connecting to jdbc:postgresql://" + postgresqlHost + ":" + postgresqlPort + "/" + dbName
                            + "?user=" + postgresqlUsername + "&password=XXXXXXXXXX");
            return DriverManager.getConnection("jdbc:postgresql://" + postgresqlHost + ":" + postgresqlPort + "/"
                            + dbName, postgresqlUsername, postgresqlPassword);
        } // createConnection

    } // PostgreSQLDriver

} // PostgreSQLBackendImpl