package com.telefonica.iot.cygnus.backends.postgresql;
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


/**
 * Interface for those backends implementing the persistence in PostgreSQL.
 * 
 * @author javimolla
 */
public interface PostgreSQLBackend {
    
    /**
     * Creates a database, given its name, if not exists.
     * @param dbName
     * @throws Exception
     */
    void createDatabase(String dbName) throws Exception;
    
    /**
     * Creates a schema, given its name, if not exists in the given database.
     * 
     * @param schemaName
     * @param tableName
     * @throws Exception
     */
    void createSchema(String dbName, String schemaName) throws Exception;
    
    /**
     * Creates a table, given its name, if not exists in the given database and schema.
     * @param dbName
     * @param schemaName
     * @param tableName
     * @param fieldNames
     * @throws Exception
     */
    void createTable(String dbName, String schemaName, String tableName, String fieldNames) throws Exception;
    
    /**
     * Insert already processed context data into the given table within the given database and schema.
     * @param dbName
     * @param schemaName
     * @param tableName
     * @param fieldNames
     * @param fieldValues
     * @throws Exception
     */
    void insertContextData(String dbName, String schemaName, String tableName, String fieldNames, String fieldValues) throws Exception;
    
} // PostgreSQLBackend
