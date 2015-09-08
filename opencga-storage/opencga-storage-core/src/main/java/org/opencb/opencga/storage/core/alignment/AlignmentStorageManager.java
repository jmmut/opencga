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

package org.opencb.opencga.storage.core.alignment;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import org.opencb.biodata.formats.alignment.io.AlignmentDataReader;
import org.opencb.biodata.formats.alignment.io.AlignmentRegionDataWriter;
import org.opencb.biodata.formats.alignment.sam.io.AlignmentBamDataReader;
import org.opencb.biodata.formats.io.FileFormatException;
import org.opencb.biodata.formats.alignment.io.AlignmentRegionDataReader;
import org.opencb.biodata.models.alignment.AlignmentRegion;
import org.opencb.commons.io.DataWriter;
import org.opencb.commons.run.Runner;
import org.opencb.commons.run.Task;
import org.opencb.commons.utils.FileUtils;
import org.opencb.opencga.storage.core.StorageManager;
import org.opencb.opencga.storage.core.alignment.adaptors.AlignmentDBAdaptor;
import org.opencb.opencga.storage.core.alignment.json.AlignmentCoverageJsonDataReader;
import org.opencb.opencga.storage.core.alignment.json.AlignmentCoverageJsonDataWriter;
import org.opencb.opencga.storage.core.alignment.json.AlignmentJsonDataReader;
import org.opencb.opencga.storage.core.alignment.json.AlignmentJsonDataWriter;
import org.opencb.opencga.storage.core.alignment.tasks.AlignmentRegionCoverageCalculatorTask;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.config.StorageEtlConfiguration;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by jacobo on 14/08/14.
 */
public abstract class AlignmentStorageManager extends StorageManager<DataWriter<AlignmentRegion>, AlignmentDBAdaptor> {

//    public static final String MEAN_COVERAGE_SIZE_LIST = "mean_coverage_size_list";
//    public static final String PLAIN = "plain";
//    public static final String TRANSFORM_REGION_SIZE = "transform.region_size";
//    public static final String TRANSFORM_COVERAGE_CHUNK_SIZE = "transform.coverage_chunk_size";
//    public static final String WRITE_COVERAGE = "transform.write_coverage";
//    public static final String STUDY = "study";
//    public static final String FILE_ID = "fileId";
//    public static final String FILE_ALIAS = "fileAlias";
//    public static final String WRITE_ALIGNMENTS = "writeAlignments";
//    public static final String INCLUDE_COVERAGE = "includeCoverage";
//    public static final String CREATE_BAM_INDEX = "createBai";
//    public static final String ENCRYPT = "encrypt";
//    public static final String COPY_FILE = "copy";
//    public static final String DB_NAME = "database.name";
//    public static final String TOOLS_SAMTOOLS = "tools.samtools";

    public enum Options {
        MEAN_COVERAGE_SIZE_LIST ("mean_coverage_size_list", Arrays.asList("200", "10000")),
        PLAIN ("plain", false),
        TRANSFORM_REGION_SIZE ("transform.region_size", 200000),
        TRANSFORM_COVERAGE_CHUNK_SIZE ("transform.coverage_chunk_size", 1000),
        WRITE_COVERAGE ("transform.write_coverage", true),
        STUDY ("study", true),
        FILE_ID ("fileId", ""),
        FILE_ALIAS ("fileAlias", ""),
        WRITE_ALIGNMENTS ("writeAlignments", false),
        INCLUDE_COVERAGE ("includeCoverage", true),
        CREATE_BAM_INDEX ("createBai", true),
        ENCRYPT ("encrypt", false),
        COPY_FILE ("copy", false),
        DB_NAME ("database.name", "opencga"),
        TOOLS_SAMTOOLS ("tools.samtools", null);

        private final String key;
        private final Object value;

        Options(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        public String key() {
            return key;
        }

        @SuppressWarnings("unchecked")
        public <T> T defaultValue() {
            return (T) value;
        }    
    }
    
    private StorageEtlConfiguration storageEtlConfiguration;

    public AlignmentStorageManager() {
        logger = LoggerFactory.getLogger(AlignmentStorageManager.class);
    }

    public AlignmentStorageManager(StorageConfiguration configuration) {
        super(configuration);
        logger = LoggerFactory.getLogger(AlignmentStorageManager.class);
    }

    public AlignmentStorageManager(String storageEngineId, StorageConfiguration configuration) {
        super(storageEngineId, configuration);
        logger = LoggerFactory.getLogger(AlignmentStorageManager.class);
    }

    @Override
    public URI extract(URI input, URI ouput) {
        return input;
    }

    @Override
    public URI preTransform(URI inputUri) throws IOException, FileFormatException {
        checkUri(inputUri, "input file");
        Path input = Paths.get(inputUri.getPath());
        checkBamFile(new FileInputStream(input.toFile()), input.getFileName().toString());  //Check if BAM file is sorted
        return inputUri;
    }

    /**
     * if FILE_ALIAS == null
     *  FILE_ALIAS = fileName - ".bam"
     *
     * if ENCRYPT
     *  Copy into the output path                   : <outputPath>/<FILE_ALIAS>.encrypt.bam                 (pending)
     * if !ENCRYPT && COPY_FILE
     *  Encrypt into the output path                : <outputPath>/<FILE_ALIAS>.bam                         (pending)
     * if CREATE_BAM_INDEX
     *  Create the bai with the samtools            : <outputPath>/<FILE_ALIAS>.bam.bai
     * if WRITE_ALIGNMENTS
     *  Write Json alignments                       : <outputPath>/<FILE_ALIAS>.bam.alignments.json[.gz]
     * if INCLUDE_COVERAGE
     *  Calculate the coverage                      : <outputPath>/<FILE_ALIAS>.bam.coverage.json[.gz]
     * if INCLUDE_COVERAGE && MEAN_COVERAGE_SIZE_LIST
     *  Calculate the meanCoverage                  : <outputPath>/<FILE_ALIAS>.bam.mean-coverage.json[.gz]
     *
     *
     * @param inputUri      Sorted bam file
     * @param pedigree      Not used
     * @param outputUri     Output path where files are created
     * @throws IOException
     * @throws FileFormatException
     */
    @Override
    public URI transform(URI inputUri, URI pedigree, URI outputUri)
            throws IOException, FileFormatException {

        Path input = Paths.get(inputUri.getPath());
        FileUtils.checkFile(input);

        Path output = Paths.get(outputUri.getPath());
        FileUtils.checkDirectory(output);

        // Check if a BAM file is passed and it is sorted.
        // Only binaries and sorted BAM files are accepted at this point.
        checkBamFile(new FileInputStream(input.toFile()), input.getFileName().toString());

        storageEtlConfiguration = configuration.getStorageEngine(storageEngineId).getAlignment();

        boolean plain = storageEtlConfiguration.getOptions().getBoolean(Options.PLAIN.key, Options.PLAIN.defaultValue());
        boolean createBai = storageEtlConfiguration.getOptions().getBoolean(Options.CREATE_BAM_INDEX.key(), Options.CREATE_BAM_INDEX.defaultValue());
        boolean includeCoverage = storageEtlConfiguration.getOptions().getBoolean(Options.INCLUDE_COVERAGE.key, Options.INCLUDE_COVERAGE.defaultValue());
        boolean writeJsonAlignments = storageEtlConfiguration.getOptions().getBoolean(Options.WRITE_ALIGNMENTS.key, Options.WRITE_ALIGNMENTS.defaultValue());

        int regionSize = storageEtlConfiguration.getOptions().getInt(Options.TRANSFORM_REGION_SIZE.key, Options.TRANSFORM_REGION_SIZE.defaultValue());

        //1 Encrypt
        //encrypt(encrypt, bamFile, fileId, output, copy);

        //2 Index (bai)
        if(createBai) {
            Path bamIndexPath = createBai(input, output);
        }

        //3 Calculate Coverage and transform

        //Tasks
        // tasks.add(new AlignmentRegionCompactorTask(new SqliteSequenceDBAdaptor(sqliteSequenceDBPath)));
        List<Task<AlignmentRegion>> tasks = new LinkedList<>();

        // Reader and Writer creation
        AlignmentDataReader reader = new AlignmentBamDataReader(input, null); //Read from sorted BamFile
        List<DataWriter<AlignmentRegion>> writers = new LinkedList<>();
//        String jsonOutputFiles = output.resolve(fileAlias + ".bam").toString();
        String jsonOutputFiles = output.resolve(input.getFileName()).toString();
        String outputFile = null;

        // We set the different coverage size regions
        if(includeCoverage) {
            AlignmentRegionCoverageCalculatorTask coverageCalculatorTask = new AlignmentRegionCoverageCalculatorTask();
            List<String> meanCoverageSizeList = storageEtlConfiguration.getOptions().getAsStringList(Options.MEAN_COVERAGE_SIZE_LIST.key);
            meanCoverageSizeList.forEach(coverageCalculatorTask::addMeanCoverageCalculator);
            tasks.add(coverageCalculatorTask);
        }

        // TODO
        // This must be deleted, alignments are not stored any more in JSON
        if(writeJsonAlignments) {
            AlignmentJsonDataWriter alignmentDataWriter = new AlignmentJsonDataWriter(reader, jsonOutputFiles, !plain);
            writers.add(new AlignmentRegionDataWriter(alignmentDataWriter));
            outputFile = alignmentDataWriter.getAlignmentFilename();
        }

        if(includeCoverage) {
            boolean writeMeanCoverage = !storageEtlConfiguration.getOptions().getList(Options.MEAN_COVERAGE_SIZE_LIST.key, Options.MEAN_COVERAGE_SIZE_LIST.defaultValue()).isEmpty();
            boolean writeCoverage = storageEtlConfiguration.getOptions().getBoolean(Options.WRITE_COVERAGE.key, Options.WRITE_COVERAGE.defaultValue());
            AlignmentCoverageJsonDataWriter alignmentCoverageJsonDataWriter =
                    new AlignmentCoverageJsonDataWriter(jsonOutputFiles, writeCoverage, writeMeanCoverage, !plain);
            alignmentCoverageJsonDataWriter.setChunkSize(
                    storageEtlConfiguration.getOptions().getInt(Options.TRANSFORM_COVERAGE_CHUNK_SIZE.key, Options.TRANSFORM_COVERAGE_CHUNK_SIZE.defaultValue()));
            writers.add(alignmentCoverageJsonDataWriter);
            if(outputFile == null) {
                outputFile = alignmentCoverageJsonDataWriter.getCoverageFilename();
            }
        }
        if(writers.isEmpty()) {
            logger.warn("No writers for transform-alignments!");
            return inputUri;
        }


        //Runner
        AlignmentRegionDataReader regionReader = new AlignmentRegionDataReader(reader);
        regionReader.setMaxSequenceSize(regionSize);
        Runner<AlignmentRegion> runner = new Runner<>(regionReader, writers, tasks, 1);

        logger.info("Transforming alignments...");
        long start = System.currentTimeMillis();
        runner.run();
        long end = System.currentTimeMillis();
        logger.info("end - start = " + (end - start) / 1000.0 + "s");

        return outputUri.resolve(outputFile);
    }

    @Override
    public URI postTransform(URI input) throws IOException, FileFormatException {
        return input;
    }

    @Override
    public boolean testConnection(String dbName) {
        return true;
    }

    protected Path encrypt(String encrypt, Path bamFile, String fileName, Path outdir, boolean copy) throws IOException {
        logger.info("Coping file. Encryption : " + encrypt);
        long start = System.currentTimeMillis();
        if(fileName == null || fileName.isEmpty()) {
            fileName = bamFile.getFileName().toString();
        } else {
            fileName += ".bam";
        }
        Path destFile;
        switch(encrypt){
            case "aes-256": {
                destFile = outdir.resolve(fileName + ".encrypt");
//                InputStream inputStream = new BufferedInputStream(new FileInputStream(sortBam.toFile()), 50000000);
//                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(bamFile.toFile()), 50000000);       //TODO: ENCRYPT OUTPUT
//
//                SAMFileReader reader = new SAMFileReader(inputStream);
//                BAMFileWriter writer = new BAMFileWriter(outputStream, bamFile.toFile());
//
//                writer.setSortOrder(reader.getFileHeader().getSortOrder(), true);   //Must be called before calling setHeader()
//                writer.setHeader(reader.getFileHeader());
//                SAMRecordIterator iterator = reader.iterator();
//                while(iterator.hasNext()){
//                    writer.addAlignment(iterator.next());
//                }
//
//                writer.close();
//                reader.close();
//                break;
                throw new UnsupportedOperationException("Encryption not supported");
            }
            default: {
                if (copy) {
                    destFile = outdir.resolve(fileName);
                    Files.copy(bamFile, destFile);
                } else {
                    logger.info("copy = false. Don't copy file.");
                    destFile = bamFile;
                }
            }
        }
        long end = System.currentTimeMillis();
        logger.info("end - start = " + (end - start) / 1000.0 + "s");
        return destFile;
    }

    @Deprecated
    protected Path sortAlignmentsFile(Path input, Path outdir) throws IOException {
        Path sortBam;
        SAMFileReader reader = new SAMFileReader(input.toFile());
        switch (reader.getFileHeader().getSortOrder()) {
            case coordinate:
                sortBam = input;
                logger.info("File sorted.");
                break;
            case queryname:
            case unsorted:
            default:
                sortBam = outdir.resolve(input.getFileName().toString() + ".sort.bam");
                String sortCommand = "samtools sort -f " + input.toAbsolutePath().toString() + " " + sortBam.toString();
                logger.info("Sorting file : " + sortCommand);
                long start = System.currentTimeMillis();
                try {
                    Runtime.getRuntime().exec( sortCommand ).waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long end = System.currentTimeMillis();
                logger.info("end - start = " + (end - start)/1000.0+"s");
                break;
        }
        reader.close();
        return sortBam;
    }


    protected AlignmentJsonDataReader getAlignmentJsonDataReader(URI input) throws IOException {
        if (!input.getScheme().equals("file")) {
            throw new IOException("URI is not a valid path");
        }

        String baseFileName = input.getPath();
        String alignmentFile = baseFileName;
        String headerFile;
        if(baseFileName.endsWith(".bam")){
            alignmentFile = baseFileName + (Paths.get(baseFileName + ".alignments.json").toFile().exists() ?
                    ".alignments.json" :
                    ".alignments.json.gz");
            headerFile = baseFileName + (Paths.get(baseFileName + ".header.json").toFile().exists() ?
                    ".header.json" :
                    ".header.json.gz");
        } else if(baseFileName.endsWith(".alignments.json")){
            headerFile = baseFileName.replaceFirst("alignments\\.json$", "header.json");
        } else if(baseFileName.endsWith(".alignments.json.gz")){
            headerFile = baseFileName.replaceFirst("alignments\\.json\\.gz$", "header.json.gz");
        } else {
            throw new IOException("Invalid input file : " + input.toString());
        }
        if (!Paths.get(alignmentFile).toFile().exists()) {
            throw new FileNotFoundException(alignmentFile);
        }
        if (!Paths.get(headerFile).toFile().exists()) {
            throw new FileNotFoundException(headerFile);
        }

        return new AlignmentJsonDataReader(alignmentFile, headerFile);
    }

    protected AlignmentCoverageJsonDataReader getAlignmentCoverageJsonDataReader(Path input) {
        String baseFileName = input.toString();
        String meanCoverageFile;
        String regionCoverageFile = baseFileName;
        if(baseFileName.endsWith(".bam")){
            regionCoverageFile = baseFileName + (Paths.get(baseFileName + ".coverage.json").toFile().exists() ?
                    ".coverage.json" :
                    ".coverage.json.gz");
            meanCoverageFile = baseFileName + (Paths.get(baseFileName + ".mean-coverage.json").toFile().exists() ?
                    ".mean-coverage.json" :
                    ".mean-coverage.json.gz");
        } else if(baseFileName.endsWith(".coverage.json")){
            meanCoverageFile = baseFileName.replaceFirst("coverage\\.json$", "mean-coverage.json");
        } else if(baseFileName.endsWith(".coverage.json.gz")){
            meanCoverageFile = baseFileName.replaceFirst("coverage\\.json\\.gz$", "mean-coverage.json.gz");
        } else {
            return null;
        }

        return new AlignmentCoverageJsonDataReader(regionCoverageFile, meanCoverageFile);
    }

    public Path createBai(Path input, Path output) throws IOException {
        //            Path bamIndexFile = output.resolve(fileAlias + ".bam.bai");
        Path bamIndexPath = output.resolve(input.getFileName().toString() + ".bai");
        if (!Files.exists(bamIndexPath)) {
            Path samtoolsPath = Paths.get(configuration.getStorageEngine(storageEngineId).getAlignment().getOptions().getString(Options.TOOLS_SAMTOOLS.key, Options.TOOLS_SAMTOOLS.defaultValue()));
            String samtoolsBin;
            if (samtoolsPath != null && samtoolsPath.toFile().exists() && samtoolsPath.toFile().canExecute()) {
                samtoolsBin = samtoolsPath.toFile().getAbsolutePath();
                logger.debug("samtools binary set to '{}' file", samtoolsBin);
            } else {
                samtoolsBin = "samtools";
                logger.debug("samtools binary taken from PATH, check configuration variable '{}: {}'", Options.TOOLS_SAMTOOLS.key, samtoolsPath);
            }

            long start = System.currentTimeMillis();
            String indexBai = samtoolsBin + " index " + input.toString(); //  + " " + bamIndexFile.toString();
            logger.info("Creating BAM index: '{}'", indexBai);
            try {
                Runtime.getRuntime().exec(indexBai).waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long end = System.currentTimeMillis();
            logger.info("end - start = " + (end - start) / 1000.0 + "s");
        }
        return bamIndexPath;
    }

    /**
     * Check if the file is a sorted binary bam file.
     * @param is            Bam InputStream
     * @param bamFileName   Bam FileName
     * @throws IOException
     */
    protected void checkBamFile(InputStream is, String bamFileName) throws IOException {
        SAMFileReader reader = new SAMFileReader(is);
        boolean binary = reader.isBinary();
        SAMFileHeader.SortOrder sortOrder = reader.getFileHeader().getSortOrder();
        reader.close();

        if (!binary) {
            throw new IOException("Expected binary SAM file. File " + bamFileName + " is not binary.");
        }

        switch (sortOrder) {
            case coordinate:
                logger.debug("BAM file '{}' is sorted.", bamFileName);
                break;
            case queryname:
            case unsorted:
            default:
                throw new IOException("Expected sorted BAM file. " +
                        "File " + bamFileName + " is an unsorted bam (" + sortOrder.name() + ")");
        }
    }

    protected void checkUri(URI uri, String uriName) throws IOException {
        if(uri == null || uri.getScheme() != null && !uri.getScheme().equals("file")) {
            throw new IOException("Expected file:// uri scheme for " + uriName);
        }
    }

}
