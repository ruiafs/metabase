(ns metabase.sync.fetch-metadata
  "Fetch metadata functions fetch 'snapshots' of the schema for a data warehouse database, including information about
  tables, schemas, and fields, and their types. For example, with SQL databases, these functions use the JDBC
  DatabaseMetaData to get this information."
  (:require
   [metabase.driver :as driver]
   [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
   [metabase.driver.util :as driver.u]
   [metabase.sync.interface :as i]
   [metabase.util.log :as log]
   [metabase.util.malli :as mu]
   [metabase.util.malli.fn :as mu.fn]))

(defmacro log-if-error
  "Logs an error message if an exception is thrown while executing the body."
  {:style/indent 1}
  [function-name & body]
  `(try
     ~@body
     (catch Throwable e#
       (log/errorf e# "Error while fetching metdata with '%s'" ~function-name)
       (throw e#))))

(mu/defn db-metadata :- i/DatabaseMetadata
  "Get basic Metadata about a `database` and its Tables. Doesn't include information about the Fields."
  [database :- i/DatabaseInstance]
  (log-if-error "db-metadata"
    (driver/describe-database (driver.u/database->driver database) database)))

(mu/defn fields-metadata
  "Effectively a wrapper for [[metabase.driver/describe-fields]] that also validates the output against the schema."
  [database :- i/DatabaseInstance & {:as args}]
  (log-if-error "fields-metadata"
    (cond->> (driver/describe-fields (driver.u/database->driver database) database args)
      ;; This is a workaround for the fact that [[mu/defn]] can't check reducible collections yet
      (mu.fn/instrument-ns? *ns*)
      (eduction (map #(mu.fn/validate-output {} i/FieldMetadataEntry %))))))

(mu/defn table-fields-metadata :- [:set i/TableMetadataField]
  "Get more detailed information about a `table` belonging to `database`. Includes information about the Fields."
  [database :- i/DatabaseInstance
   table    :- i/TableInstance]
  (log-if-error "table-fields-metadata"
    (if (driver/database-supports? (driver.u/database->driver database) :describe-fields database)
      (set (fields-metadata database :table-names [(:name table)] :schema-names [(:schema table)]))
      (:fields (driver/describe-table (driver.u/database->driver database) database table)))))

(mu/defn fk-metadata
  "Effectively a wrapper for [[metabase.driver/describe-fks]] that also validates the output against the schema."
  [database :- i/DatabaseInstance & {:as args}]
  (log-if-error "fk-metadata"
    (cond->> (driver/describe-fks (driver.u/database->driver database) database args)
      ;; This is a workaround for the fact that [[mu/defn]] can't check reducible collections yet
      (mu.fn/instrument-ns? *ns*)
      (eduction (map #(mu.fn/validate-output {} i/FKMetadataEntry %))))))

(mu/defn table-fk-metadata :- [:maybe [:sequential i/FKMetadataEntry]]
  "Get information about the foreign keys belonging to `table`."
  [database :- i/DatabaseInstance
   table    :- i/TableInstance]
  (log-if-error "table-fk-metadata"
    (let [driver (driver.u/database->driver database)]
      (when (driver/database-supports? driver :foreign-keys database)
        (if (driver/database-supports? driver :describe-fks database)
          (vec (driver/describe-fks driver database :table-names [(:name table)] :schema-names [(:schema table)]))
          #_{:clj-kondo/ignore [:deprecated-var]}
          (vec (for [x (driver/describe-table-fks driver database table)]
                 {:fk-table-name   (:name table)
                  :fk-table-schema (:schema table)
                  :fk-column-name  (:fk-column-name x)
                  :pk-table-name   (:name (:dest-table x))
                  :pk-table-schema (:schema (:dest-table x))
                  :pk-column-name  (:dest-column-name x)})))))))

(mu/defn nfc-metadata :- [:maybe [:set i/TableMetadataField]]
  "Get information about the nested field column fields within `table`."
  [database :- i/DatabaseInstance
   table    :- i/TableInstance]
  (log-if-error "nfc-metadata"
    (let [driver (driver.u/database->driver database)]
      (when (driver/database-supports? driver :nested-field-columns database)
        (sql-jdbc.sync/describe-nested-field-columns driver database table)))))

(mu/defn index-metadata :- [:maybe i/TableIndexMetadata]
  "Get information about the indexes belonging to `table`."
  [database :- i/DatabaseInstance
   table    :- i/TableInstance]
  (log-if-error "index-metadata"
    (driver/describe-table-indexes (driver.u/database->driver database) database table)))
