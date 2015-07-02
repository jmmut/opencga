/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.mongodb.variant;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencb.biodata.formats.variant.io.VariantReader;
import org.opencb.biodata.formats.variant.io.VariantWriter;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.tools.variant.tasks.VariantRunner;
import org.opencb.commons.containers.list.SortedList;
import org.opencb.commons.io.DataWriter;
import org.opencb.commons.run.ParallelTaskRunner;
import org.opencb.commons.run.Task;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.config.DataStoreServerAddress;
import org.opencb.opencga.core.auth.IllegalOpenCGACredentialsException;

import org.opencb.opencga.storage.core.StudyConfiguration;
import org.opencb.opencga.storage.core.StorageManagerException;
import org.opencb.opencga.storage.core.variant.FileStudyConfigurationManager;
import org.opencb.opencga.storage.core.variant.StudyConfigurationManager;
import org.opencb.opencga.storage.core.variant.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.mongodb.utils.MongoCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by imedina on 13/08/14.
 */
public class MongoDBVariantStorageManager extends VariantStorageManager {

    /**
     * This field defaultValue must be the same that the one at storage-configuration.yml
     */
    public static final String STORAGE_ENGINE_ID = "mongodb";

    //StorageEngine specific Properties
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_HOSTS                 = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.HOSTS";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_AUTHENTICATION_DB     = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.AUTHENTICATION.DB";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_NAME                  = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.NAME";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_USER                  = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.USER";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_PASS                  = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.PASS";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_COLLECTION_VARIANTS   = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.COLLECTION.VARIANTS";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DB_COLLECTION_FILES      = "OPENCGA.STORAGE.MONGODB.VARIANT.DB.COLLECTION.FILES";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_LOAD_BATCH_SIZE          = "OPENCGA.STORAGE.MONGODB.VARIANT.LOAD.BATCH_SIZE";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_LOAD_BULK_SIZE           = "OPENCGA.STORAGE.MONGODB.VARIANT.LOAD.BULK_SIZE";
//  @Deprecated   public static final String OPENCGA_STORAGE_MONGODB_VARIANT_LOAD_WRITE_THREADS       = "OPENCGA.STORAGE.MONGODB.VARIANT.LOAD.WRITE_THREADS";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_DEFAULT_GENOTYPE         = "OPENCGA.STORAGE.MONGODB.VARIANT.LOAD.DEFAULT_GENOTYPE";
    @Deprecated public static final String OPENCGA_STORAGE_MONGODB_VARIANT_COMPRESS_GENEOTYPES      = "OPENCGA.STORAGE.MONGODB.VARIANT.LOAD.COMPRESS_GENOTYPES";

    //StorageEngine specific options
//    public static final String WRITE_MONGO_THREADS = "writeMongoThreads";
    public static final String AUTHENTICATION_DB     = "authentication.db";
    public static final String COLLECTION_VARIANTS   = "collection.variants";
    public static final String COLLECTION_FILES      = "collection.files";
    public static final String BULK_SIZE = "bulkSize";
    public static final String DEFAULT_GENOTYPE = "defaultGenotype";

    protected static Logger logger = LoggerFactory.getLogger(MongoDBVariantStorageManager.class);

    @Override
    public VariantMongoDBWriter getDBWriter(String dbName) {
        ObjectMap options = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions();
        StudyConfiguration studyConfiguration = getStudyConfiguration(options);
        int fileId = options.getInt(Options.FILE_ID.key());

//        Properties credentialsProperties = new Properties(properties);

        MongoCredentials credentials = getMongoCredentials(dbName);
        String variantsCollection = options.getString(COLLECTION_VARIANTS, "variants");
        String filesCollection = options.getString(COLLECTION_FILES, "files");
        logger.debug("getting DBWriter to db: {}", credentials.getMongoDbName());
        return new VariantMongoDBWriter(fileId, studyConfiguration, credentials, variantsCollection, filesCollection, false, false);
    }

    @Override
    public VariantMongoDBAdaptor getDBAdaptor(String dbName) {
        MongoCredentials credentials = getMongoCredentials(dbName);
        VariantMongoDBAdaptor variantMongoDBAdaptor;
        ObjectMap options = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions();

        String variantsCollection = options.getString(COLLECTION_VARIANTS, "variants");
        String filesCollection = options.getString(COLLECTION_FILES, "files");
        try {
            variantMongoDBAdaptor = new VariantMongoDBAdaptor(credentials, variantsCollection, filesCollection);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }

        logger.debug("getting DBAdaptor to db: {}", credentials.getMongoDbName());
        return variantMongoDBAdaptor;
    }


    /* package */ MongoCredentials getMongoCredentials() {
        return getMongoCredentials(null);
    }

    /* package */ MongoCredentials getMongoCredentials(String dbName) {
        ObjectMap options = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions();

        List<DataStoreServerAddress> dataStoreServerAddresses = new LinkedList<>();
        for (String host : configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getDatabase().getHosts()) {
            if (host.contains(":")) {
                String[] hostPort = host.split(":");
                dataStoreServerAddresses.add(new DataStoreServerAddress(hostPort[0], Integer.parseInt(hostPort[1])));
            } else {
                dataStoreServerAddresses.add(new DataStoreServerAddress(host, 27017));
            }
        }

        if(dbName == null || dbName.isEmpty()) {    //If no database name is provided, read from the configuration file
            dbName = options.getString(Options.DB_NAME.key(), Options.DB_NAME.defaultValue());
        }
        String user = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getDatabase().getUser();
        String pass = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getDatabase().getPassword();

        String authenticationDatabase = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions().getString(AUTHENTICATION_DB, null);

        try {
            MongoCredentials mongoCredentials = new MongoCredentials(dataStoreServerAddresses, dbName, user, pass);
            mongoCredentials.setAuthenticationDatabase(authenticationDatabase);
            return mongoCredentials;
        } catch (IllegalOpenCGACredentialsException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public URI preLoad(URI input, URI output) throws StorageManagerException {
        return super.preLoad(input, output);
    }

    @Override
    public URI load(URI inputUri) throws IOException, StorageManagerException {
        // input: getDBSchemaReader
        // output: getDBWriter()
        ObjectMap options = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions();

        Path input = Paths.get(inputUri.getPath());

        boolean includeSamples = options.getBoolean(Options.INCLUDE_GENOTYPES.key(), false);
        boolean includeStats = options.getBoolean(Options.INCLUDE_STATS.key(), false);
        boolean includeSrc = options.getBoolean(Options.INCLUDE_SRC.key(), false);

        String defaultGenotype = options.getString(DEFAULT_GENOTYPE, "");
        boolean compressSamples = options.getBoolean(Options.COMPRESS_GENOTYPES.key(), false);

        VariantSource source = new VariantSource(inputUri.getPath(), "", "", "");       //Create a new VariantSource. This object will be filled at the VariantJsonReader in the pre()
//        params.put(VARIANT_SOURCE, source);
        String dbName = options.getString(Options.DB_NAME.key(), null);

//        VariantSource variantSource = readVariantSource(input, null);
//        new StudyInformation(variantSource.getStudyId())

        int batchSize = options.getInt(Options.LOAD_BATCH_SIZE.key(), 100);
        int bulkSize = options.getInt(BULK_SIZE, batchSize);
        int loadThreads = options.getInt(Options.LOAD_THREADS.key(), 8);
        int capacity = options.getInt("blockingQueueCapacity", loadThreads*2);
//        int numWriters = params.getInt(WRITE_MONGO_THREADS, Integer.parseInt(properties.getProperty(OPENCGA_STORAGE_MONGODB_VARIANT_LOAD_WRITE_THREADS, "8")));
        final int numReaders = 1;
        final int numWriters = loadThreads  == 1? 1 : loadThreads - numReaders; //Subtract the reader thread


        //Reader
        VariantReader variantJsonReader;
        variantJsonReader = getVariantJsonReader(input, source);

        //Tasks
        List<Task<Variant>> taskList = new SortedList<>();


        //Writers
        List<VariantWriter> writers = new LinkedList<>();
        List<DataWriter> writerList = new LinkedList<>();
        AtomicBoolean atomicBoolean = new AtomicBoolean();
        for (int i = 0; i < numWriters; i++) {
            VariantMongoDBWriter variantDBWriter = this.getDBWriter(dbName);
            variantDBWriter.setBulkSize(bulkSize);
            variantDBWriter.includeSrc(includeSrc);
            variantDBWriter.includeSamples(includeSamples);
            variantDBWriter.includeStats(includeStats);
            variantDBWriter.setCompressDefaultGenotype(compressSamples);
            variantDBWriter.setDefaultGenotype(defaultGenotype);
            variantDBWriter.setVariantSource(source);
//            variantDBWriter.setSamplesIds(samplesIds);
            variantDBWriter.setThreadSyncronizationBoolean(atomicBoolean);
            writerList.add(variantDBWriter);
            writers.add(variantDBWriter);
        }



        logger.info("Loading variants...");
        long start = System.currentTimeMillis();

        //Runner
        if (loadThreads == 1) {
            logger.info("Single thread load...");
            VariantRunner vr = new VariantRunner(source, variantJsonReader, null, writers, taskList, batchSize);
            vr.run();
        } else {
            logger.info("Multi thread load... [{} readerThreads, {} writerThreads]", numReaders, numWriters);
//            ThreadRunner runner = new ThreadRunner(Executors.newFixedThreadPool(loadThreads), batchSize);
//            ThreadRunner.ReadNode<Variant> variantReadNode = runner.newReaderNode(variantJsonReader, 1);
//            ThreadRunner.WriterNode<Variant> variantWriterNode = runner.newWriterNode(writerList);
//
//            variantReadNode.append(variantWriterNode);
//            runner.run();


            ParallelTaskRunner<Variant, Variant> ptr;
            try {
                class TaskWriter implements ParallelTaskRunner.Task<Variant, Variant> {
                    private DataWriter<Variant> writer;

                    public TaskWriter(DataWriter<Variant> writer) {
                        this.writer = writer;
                    }

                    @Override
                    public void pre() {
                        writer.pre();
                    }

                    @Override
                    public List<Variant> apply(List<Variant> batch) {
                        writer.write(batch);
                        return batch;
                    }

                    @Override
                    public void post() {
//                        writer.post();
                    }
                }

                List<ParallelTaskRunner.Task<Variant, Variant>> tasks = new LinkedList<>();
                for (VariantWriter writer : writers) {
                    tasks.add(new TaskWriter(writer));
                }

                ptr = new ParallelTaskRunner<>(
                        variantJsonReader,
                        tasks,
                        null,
                        new ParallelTaskRunner.Config(loadThreads, batchSize, capacity, false)
                );
            } catch (Exception e) {
                e.printStackTrace();
                throw new StorageManagerException("Error while creating ParallelTaskRunner", e);
            }

            try {
                writers.forEach(DataWriter::open);
                ptr.run();
                writers.forEach(DataWriter::post);
                writers.forEach(DataWriter::close);
            } catch (ExecutionException e) {
                e.printStackTrace();
                throw new StorageManagerException("Error while executing LoadVariants in ParallelTaskRunner", e);
            }

//            SimpleThreadRunner threadRunner = new SimpleThreadRunner(
//                    variantJsonReader,
//                    Collections.<Task>emptyList(),
//                    writerList,
//                    batchSize,
//                    loadThreads * 2,
//                    0);
//            threadRunner.run();

        }

        long end = System.currentTimeMillis();
        logger.info("end - start = " + (end - start) / 1000.0 + "s");
        logger.info("Variants loaded!");

        return inputUri; //TODO: Return something like this: mongo://<host>/<dbName>/<collectionName>
    }

    @Override
    public URI postLoad(URI input, URI output) throws IOException, StorageManagerException {
        return super.postLoad(input, output);
    }

    /* --------------------------------------- */
    /*  StudyConfiguration utils methods       */
    /* --------------------------------------- */

    @Override
    protected StudyConfigurationManager buildStudyConfigurationManager(ObjectMap options) {
        if (options != null && !options.getString(FileStudyConfigurationManager.STUDY_CONFIGURATION_PATH, "").isEmpty()) {
            return super.buildStudyConfigurationManager(options);
        } else {
            String string = options == null? null : options.getString(Options.DB_NAME.key());
            return getDBAdaptor(string).getStudyConfigurationDBAdaptor();
        }
    }

    @Override
    public void checkStudyConfiguration(StudyConfiguration studyConfiguration, VariantDBAdaptor dbAdaptor) throws StorageManagerException {
        super.checkStudyConfiguration(studyConfiguration, dbAdaptor);
//        if (dbAdaptor == null) {
//            logger.debug("Do not check StudyConfiguration against the loaded in MongoDB");
//        } else {
//            if (dbAdaptor instanceof VariantMongoDBAdaptor) {
//                VariantMongoDBAdaptor mongoDBAdaptor = (VariantMongoDBAdaptor) dbAdaptor;
//                StudyConfigurationManager studyConfigurationDBAdaptor = mongoDBAdaptor.getStudyConfigurationDBAdaptor();
//                StudyConfiguration studyConfigurationFromMongo = studyConfigurationDBAdaptor.getStudyConfiguration(studyConfiguration.getStudyId(), null).first();
//
//                //Check that the provided StudyConfiguration has the same or more information that the stored in MongoDB.
//                for (Map.Entry<String, Integer> entry : studyConfigurationFromMongo.getFileIds().entrySet()) {
//                    if (!studyConfiguration.getFileIds().containsKey(entry.getKey())) {
//                        throw new StorageManagerException("StudyConfiguration do not have the file " + entry.getKey());
//                    }
//                    if (!studyConfiguration.getFileIds().get(entry.getKey()).equals(entry.getValue())) {
//                        throw new StorageManagerException("StudyConfiguration changes the fileId of '" + entry.getKey() + "' from " + entry.getValue() + " to " + studyConfiguration.getFileIds().get(entry.getKey()));
//                    }
//                }
//                for (Map.Entry<String, Integer> entry : studyConfigurationFromMongo.getCohortIds().entrySet()) {
//                    if (!studyConfiguration.getCohortIds().containsKey(entry.getKey())) {
//                        throw new StorageManagerException("StudyConfiguration do not have the cohort " + entry.getKey());
//                    }
//                    if (!studyConfiguration.getCohortIds().get(entry.getKey()).equals(entry.getValue())) {
//                        throw new StorageManagerException("StudyConfiguration changes the cohortId of '" + entry.getKey() + "' from " + entry.getValue() + " to " + studyConfiguration.getCohortIds().get(entry.getKey()));
//                    }
//                }
//                for (Map.Entry<String, Integer> entry : studyConfigurationFromMongo.getSampleIds().entrySet()) {
//                    if (!studyConfiguration.getSampleIds().containsKey(entry.getKey())) {
//                        throw new StorageManagerException("StudyConfiguration do not have the sample " + entry.getKey());
//                    }
//                    if (!studyConfiguration.getSampleIds().get(entry.getKey()).equals(entry.getValue())) {
//                        throw new StorageManagerException("StudyConfiguration changes the sampleId of '" + entry.getKey() + "' from " + entry.getValue() + " to " + studyConfiguration.getSampleIds().get(entry.getKey()));
//                    }
//                }
//                studyConfigurationDBAdaptor.updateStudyConfiguration(studyConfiguration, null);
//            } else {
//                throw new StorageManagerException("Unknown VariantDBAdaptor '" + dbAdaptor.getClass().toString() + "'. Expected '" + VariantMongoDBAdaptor.class + "'");
//            }
//        }
    }
}
