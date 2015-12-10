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

package com.telefonica.iot.cygnus.sinks;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import org.apache.flume.Context;

import com.telefonica.iot.cygnus.backends.postgresql.PostgreSQLBackendImpl;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextAttribute;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextElement;
import com.telefonica.iot.cygnus.errors.CygnusBadConfiguration;
import com.telefonica.iot.cygnus.log.CygnusLogger;
import com.telefonica.iot.cygnus.utils.Constants;
import com.telefonica.iot.cygnus.utils.Utils;

/**
 *
 * @author javimolla
 * 
 * Detailed documentation can be found at:
 * https://github.com/telefonicaid/fiware-cygnus/blob/master/doc/flume_extensions_catalogue/orion_postgresql_sink.md
 */
public class OrionPostgreSQLSink extends OrionSink {
    
    private static final CygnusLogger LOGGER = new CygnusLogger(OrionPostgreSQLSink.class);
    private String postgresqlHost;
    private String postgresqlPort;
    private String postgresqlUsername;
    private String postgresqlPassword;
    private boolean rowAttrPersistence;
    private PostgreSQLBackendImpl persistenceBackend;
    
    /**
     * Constructor.
     */
    public OrionPostgreSQLSink() {
        super();
    } // OrionPostgreSQLSink
    
    /**
     * Gets the PostgreSQL host. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL host
     */
    protected String getPostgreSQLHost() {
        return postgresqlHost;
    } // getPostgreSQLHost
    
    /**
     * Gets the PostgreSQL port. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL port
     */
    protected String getPostgreSQLPort() {
        return postgresqlPort;
    } // getPostgreSQLPort
    
    /**
     * Gets the PostgreSQL username. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL username
     */
    protected String getPostgreSQLUsername() {
        return postgresqlUsername;
    } // getPostgreSQLUsername
    
    /**
     * Gets the PostgreSQL password. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL password
     */
    protected String getPostgreSQLPassword() {
        return postgresqlPassword;
    } // getPostgreSQLPassword
    
    /**
     * Returns if the attribute persistence is row-based. It is protected due to it is only required for testing
     * purposes.
     * @return True if the attribute persistence is row-based, false otherwise
     */
    protected boolean getRowAttrPersistence() {
        return rowAttrPersistence;
    } // getRowAttrPersistence
    
    /**
     * Returns the persistence backend. It is protected due to it is only required for testing purposes.
     * @return The persistence backend
     */
    protected PostgreSQLBackendImpl getPersistenceBackend() {
        return persistenceBackend;
    } // getPersistenceBackend
    
    /**
     * Sets the persistence backend. It is protected due to it is only required for testing purposes.
     * @param persistenceBackend
     */
    protected void setPersistenceBackend(PostgreSQLBackendImpl persistenceBackend) {
        this.persistenceBackend = persistenceBackend;
    } // setPersistenceBackend
    
    @Override
    public void configure(Context context) {
        postgresqlHost = context.getString("postgresql_host", "localhost");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_host=" + postgresqlHost + ")");
        postgresqlPort = context.getString("postgresql_port", "5432");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_port=" + postgresqlPort + ")");
        postgresqlUsername = context.getString("postgresql_username", "opendata");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_username=" + postgresqlUsername + ")");
        // FIXME: postgresqlPassword should be read as a SHA1 and decoded here
        postgresqlPassword = context.getString("postgresql_password", "unknown");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgresql_password=" + postgresqlPassword + ")");
        rowAttrPersistence = context.getString("attr_persistence", "row").equals("row");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (attr_persistence="
                + (rowAttrPersistence ? "row" : "column") + ")");
        super.configure(context);
    } // configure

    @Override
    public void start() {
        // create the persistence backend
        LOGGER.debug("[" + this.getName() + "] PostgreSQL persistence backend created");
        persistenceBackend = new PostgreSQLBackendImpl(postgresqlHost, postgresqlPort, postgresqlUsername, postgresqlPassword);
        super.start();
        LOGGER.info("[" + this.getName() + "] Startup completed");
    } // start

    @Override
    void persistOne(Map<String, String> eventHeaders, NotifyContextRequest notification) throws Exception {
        Accumulator accumulator = new Accumulator();
        accumulator.initializeBatching(new Date().getTime());
        accumulator.accumulate(eventHeaders, notification);
        persistBatch(accumulator.getDefaultBatch(), accumulator.getGroupedBatch());
    } // persistOne
    
    @Override
    void persistBatch(Batch defaultBatch, Batch groupedBatch) throws Exception {
        // select batch depending on the enable grouping parameter
        Batch batch = (enableGrouping ? groupedBatch : defaultBatch);
        
        if (batch == null) {
            LOGGER.debug("[" + this.getName() + "] Null batch, nothing to do");
            return;
        } // if
 
        // iterate on the destinations, for each one a single create / append will be performed
        for (String destination : batch.getDestinations()) {
            LOGGER.debug("[" + this.getName() + "] Processing sub-batch regarding the " + destination
                    + " destination");

            // get the sub-batch for this destination
            ArrayList<CygnusEvent> subBatch = batch.getEvents(destination);
            
            // get an aggregator for this destination and initialize it
            PostgreSQLAggregator aggregator = getAggregator(rowAttrPersistence);
            aggregator.initialize(subBatch.get(0));

            for (CygnusEvent cygnusEvent : subBatch) {
                aggregator.aggregate(cygnusEvent);
            } // for
            
            // persist the fieldValues
            persistAggregation(aggregator);
            batch.setPersisted(destination);
        } // for
    } // persistBatch
    
    /**
     * Class for aggregating fieldValues.
     */
    private abstract class PostgreSQLAggregator {
        
        // string containing the data fieldValues
        protected String aggregation;

        protected String service;
        protected String servicePath;
        protected String destination;
        protected String dbName;
        protected String schemaName;
        protected String tableName;
        protected String typedFieldNames;
        protected String fieldNames;
        
        public PostgreSQLAggregator() {
            aggregation = "";
        } // PostgreSQLAggregator
        
        public String getAggregation() {
            return aggregation;
        } // getAggregation
        
        public String getDbName() {
            return dbName;
        } // getDbName
        
        public String getSchemaName() {
            return schemaName;
        } // getSchemaName
        
        public String getTableName() {
            return tableName;
        } // getTableName
        
        public String getTypedFieldNames() {
            return typedFieldNames;
        } // getTypedFieldNames
        
        public String getFieldNames() {
            return fieldNames;
        } // getFieldNames
        
        public void initialize(CygnusEvent cygnusEvent) throws Exception {
            service = cygnusEvent.getService();
            servicePath = cygnusEvent.getServicePath();
            destination = cygnusEvent.getDestination();
            dbName = buildDbName(service);
            schemaName = buildSchemaName(servicePath);
            tableName = buildTableName(destination);
        } // initialize
        
        public abstract void aggregate(CygnusEvent cygnusEvent) throws Exception;
        
    } // PostgreSQLAggregator
    
    /**
     * Class for aggregating batches in row mode.
     */
    private class RowAggregator extends PostgreSQLAggregator {
        
        @Override
        public void initialize(CygnusEvent cygnusEvent) throws Exception {
            super.initialize(cygnusEvent);
            typedFieldNames = "("
                    + Constants.RECV_TIME_TS + " bigint,"
                    + Constants.RECV_TIME + " text,"
                    + Constants.HEADER_NOTIFIED_SERVICE_PATH.replaceAll("-", "") + " text,"
                    + Constants.ENTITY_ID + " text,"
                    + Constants.ENTITY_TYPE + " text,"
                    + Constants.ATTR_NAME + " text,"
                    + Constants.ATTR_TYPE + " text,"
                    + Constants.ATTR_VALUE + " text,"
                    + Constants.ATTR_MD + " text"
                    + ")";
            fieldNames = "("
                    + Constants.RECV_TIME_TS + ","
                    + Constants.RECV_TIME + ","
                    + Constants.HEADER_NOTIFIED_SERVICE_PATH.replaceAll("-", "") + ","
                    + Constants.ENTITY_ID + ","
                    + Constants.ENTITY_TYPE + ","
                    + Constants.ATTR_NAME + ","
                    + Constants.ATTR_TYPE + ","
                    + Constants.ATTR_VALUE + ","
                    + Constants.ATTR_MD
                    + ")";
        } // initialize
        
        @Override
        public void aggregate(CygnusEvent cygnusEvent) throws Exception {
            // get the event headers
            long recvTimeTs = cygnusEvent.getRecvTimeTs();
            String recvTime = Utils.getHumanReadable(recvTimeTs, true);

            // get the event body
            ContextElement contextElement = cygnusEvent.getContextElement();
            String entityId = contextElement.getId();
            String entityType = contextElement.getType();
            LOGGER.debug("[" + getName() + "] Processing context element (id=" + entityId + ", type="
                    + entityType + ")");
            
            // iterate on all this context element attributes, if there are attributes
            ArrayList<ContextAttribute> contextAttributes = contextElement.getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                LOGGER.warn("No attributes within the notified entity, nothing is done (id=" + entityId
                        + ", type=" + entityType + ")");
                return;
            } // if
            
            for (ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                String attrType = contextAttribute.getType();
                String attrValue = contextAttribute.getContextValue(false);
                String attrMetadata = contextAttribute.getContextMetadata();
                LOGGER.debug("[" + getName() + "] Processing context attribute (name=" + attrName + ", type="
                        + attrType + ")");
                
                // create a column and aggregate it
                String row = "('"
                    + recvTimeTs + "','"
                    + recvTime + "','"
                    + servicePath + "','"
                    + entityId + "','"
                    + entityType + "','"
                    + attrName + "','"
                    + attrType + "','"
                    + attrValue + "','"
                    + attrMetadata
                    + "')";
                
                if (aggregation.isEmpty()) {
                    aggregation += row;
                } else {
                    aggregation += "," + row;
                } // if else
            } // for
        } // aggregate

    } // RowAggregator
    
    /**
     * Class for aggregating batches in column mode.
     */
    private class ColumnAggregator extends PostgreSQLAggregator {

        @Override
        public void initialize(CygnusEvent cygnusEvent) throws Exception {
            super.initialize(cygnusEvent);
            
            // particulat initialization
            typedFieldNames = "(" + Constants.RECV_TIME + " text,"
                    + Constants.HEADER_NOTIFIED_SERVICE_PATH.replaceAll("-", "") + " text,"
                    + Constants.ENTITY_ID + " text,"
                    + Constants.ENTITY_TYPE + " text";
            fieldNames = "(" + Constants.RECV_TIME + ","
                    + Constants.HEADER_NOTIFIED_SERVICE_PATH.replaceAll("-", "") + ","
                    + Constants.ENTITY_ID + ","
                    + Constants.ENTITY_TYPE;
            
            // iterate on all this context element attributes, if there are attributes
            ArrayList<ContextAttribute> contextAttributes = cygnusEvent.getContextElement().getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                return;
            } // if
            
            for (ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                typedFieldNames += "," + attrName + " text," + attrName + "_md text";
                fieldNames += "," + attrName + "," + attrName + "_md";
            } // for
            
            typedFieldNames += ")";
            fieldNames += ")";
        } // initialize
        
        @Override
        public void aggregate(CygnusEvent cygnusEvent) throws Exception {
            // get the event headers
            long recvTimeTs = cygnusEvent.getRecvTimeTs();
            String recvTime = Utils.getHumanReadable(recvTimeTs, true);

            // get the event body
            ContextElement contextElement = cygnusEvent.getContextElement();
            String entityId = contextElement.getId();
            String entityType = contextElement.getType();
            LOGGER.debug("[" + getName() + "] Processing context element (id=" + entityId + ", type="
                    + entityType + ")");
            
            // iterate on all this context element attributes, if there are attributes
            ArrayList<ContextAttribute> contextAttributes = contextElement.getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                LOGGER.warn("No attributes within the notified entity, nothing is done (id=" + entityId
                        + ", type=" + entityType + ")");
                return;
            } // if
            
            String column = "('" + recvTime + "','" + servicePath + "','" + entityId + "','" + entityType + "'";
            
            for (ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                String attrType = contextAttribute.getType();
                String attrValue = contextAttribute.getContextValue(false);
                String attrMetadata = contextAttribute.getContextMetadata();
                LOGGER.debug("[" + getName() + "] Processing context attribute (name=" + attrName + ", type="
                        + attrType + ")");
                
                // create part of the column with the current attribute (a.k.a. a column)
                column += ",'" + attrValue + "','"  + attrMetadata + "'";
            } // for
            
            // now, aggregate the column
            if (aggregation.isEmpty()) {
                aggregation += column + ")";
            } else {
                aggregation += "," + column + ")";
            } // if else
        } // aggregate
        
    } // ColumnAggregator
    
    private PostgreSQLAggregator getAggregator(boolean rowAttrPersistence) {
        if (rowAttrPersistence) {
            return new RowAggregator();
        } else {
            return new ColumnAggregator();
        } // if else
    } // getAggregator
    
    private void persistAggregation(PostgreSQLAggregator aggregator) throws Exception {
        String typedFieldNames = aggregator.getTypedFieldNames();
        String fieldNames = aggregator.getFieldNames();
        String fieldValues = aggregator.getAggregation();
        String dbName = aggregator.getDbName();
        String schemaName = aggregator.getSchemaName();
        String tableName = aggregator.getTableName();
        
        LOGGER.info("[" + this.getName() + "] Persisting data at OrionPostgreSQLSink. Database ("
                + dbName + "), Table (" + tableName + "), Fields (" + fieldNames + "), Values ("
                + fieldValues + ")");
        
        // creating the database and the table has only sense if working in row mode, in column node
        // everything must be provisioned in advance
        if (aggregator instanceof RowAggregator) {
            persistenceBackend.createDatabase(dbName);
            persistenceBackend.createSchema(dbName, schemaName);
            persistenceBackend.createTable(dbName, schemaName, tableName, typedFieldNames);
        } // if
        
        persistenceBackend.insertContextData(dbName, schemaName, tableName, fieldNames, fieldValues);
    } // persistAggregation

    /**
     * Builds a database name given a fiwareService. It throws an exception if the naming conventions are violated.
     * @param fiwareService
     * @return
     * @throws Exception
     */
    private String buildDbName(String fiwareService) throws Exception {
        String dbName = fiwareService;
        
        if (dbName.length() > Constants.MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building dbName=fiwareService (" + dbName + ") and its length is greater "
                    + "than " + Constants.MAX_NAME_LEN);
        } // if
        
        return dbName;
    } // buildDbName
    
    /**
     * Builds a package name given a destination. It throws an exception if the naming
     * conventions are violated.
     * @param fiwarePath
     * @return
     * @throws Exception
     */
    private String buildSchemaName(String fiwarePath) throws Exception {
        String schemaName = fiwarePath;

        if (schemaName.length() > Constants.MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building schemaName=fiwarePath (" + schemaName
                    + ") and its length is greater than " + Constants.MAX_NAME_LEN);
        } // if
        
        return schemaName;
    } // buildSchemaName
    
    /**
     * Builds a package name given a destination. It throws an exception if the naming
     * conventions are violated.
     * @param destination
     * @return
     * @throws Exception
     */
    private String buildTableName(String destination) throws Exception {
        String tableName = destination;

        if (tableName.length() > Constants.MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building tableName=destination (" + tableName
                    + ") and its length is greater than " + Constants.MAX_NAME_LEN);
        } // if
        
        return tableName;
    } // buildTableName

} // OrionPostgreSQLSink