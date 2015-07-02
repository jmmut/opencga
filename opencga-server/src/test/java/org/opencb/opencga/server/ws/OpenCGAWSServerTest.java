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

package org.opencb.opencga.server.ws;

import org.apache.tools.ant.types.Commandline;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opencb.biodata.models.variant.Variant;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.mongodb.MongoDataStoreManager;
import org.opencb.opencga.analysis.AnalysisExecutionException;
import org.opencb.opencga.analysis.AnalysisJobExecutor;
import org.opencb.opencga.analysis.storage.AnalysisFileIndexer;
import org.opencb.opencga.catalog.CatalogManagerTest;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.core.common.Config;
import org.opencb.opencga.storage.core.alignment.adaptors.AlignmentDBAdaptor;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OpenCGAWSServerTest {

    public static final String TEST_SERVER_USER = "test_server_user";
    private static WSServerTestUtils serverTestUtils;
    private WebTarget webTarget;

    @BeforeClass
    static public void initServer() throws Exception {
        serverTestUtils = new WSServerTestUtils();
        serverTestUtils.initServer();
    }

    @AfterClass
    static public void shutdownServer() throws Exception {
        serverTestUtils.shutdownServer();
    }


    @Before
    public void init() throws Exception {

        //Drop default user mongoDB database.
        String databaseName = WSServerTestUtils.DATABASE_PREFIX + TEST_SERVER_USER + "_" + ProjectWSServerTest.PROJECT_ALIAS;
        new MongoDataStoreManager("localhost", 27017).drop(databaseName);

        serverTestUtils.setUp();
        webTarget = serverTestUtils.getWebTarget();

    }

    /** First echo message to test Server connectivity **/
    @Test
    public void testConnectivity() throws InterruptedException, IOException {
        String message = "Test";
        WebTarget testPath = webTarget.path("test").path("echo").path(message);
        System.out.println("testPath = " + testPath);
        String s = testPath.request().get(String.class);
        assertEquals("Expected [" + message + "], actual [" + s + "]", message, s);

        testPath = webTarget.path("test").path("echo");
        System.out.println("testPath = " + testPath);
        MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        FormDataBodyPart bodyPart = new FormDataBodyPart("message", message);
        multiPart.bodyPart(bodyPart);

        s = testPath.request().post(Entity.entity(multiPart, multiPart.getMediaType()), String.class);
        assertEquals("Expected [" + message + "], actual [" + s + "]", message, s);
    }

    /** User tests **/
    @Test
    public void userTests() throws IOException {
        UserWSServerTest userTest = new UserWSServerTest(webTarget);
        User user = userTest.createUser(TEST_SERVER_USER);
        String sessionId = userTest.loginUser(user.getId());
        userTest.updateUser(user.getId(), sessionId);
    }

    @Test
    public void workflowCreation() throws Exception {
        UserWSServerTest userTest = new UserWSServerTest(webTarget);
        User user = userTest.createUser(TEST_SERVER_USER);
        String sessionId = userTest.loginUser(user.getId());
        user = userTest.info(user.getId(), sessionId);

        ProjectWSServerTest prTest = new ProjectWSServerTest(webTarget);
        Project project = prTest.createProject(user.getId(), sessionId);
        prTest.modifyProject(project.getId(), sessionId);
        project = prTest.info(project.getId(), sessionId);
        userTest.getAllProjects(user.getId(), sessionId);

        StudyWSServerTest stTest = new StudyWSServerTest(webTarget);
        Study study = stTest.createStudy(project.getId(), sessionId);
        stTest.modifyStudy(study.getId(), sessionId);
        study = stTest.info(study.getId(), sessionId);
        prTest.getAllStudies(project.getId(), sessionId);

        FileWSServerTest fileTest = new FileWSServerTest(); fileTest.setWebTarget(webTarget);
        File fileVcf = fileTest.uploadVcf(study.getId(), sessionId);
        assertEquals(File.Status.READY, fileVcf.getStatus());
        assertEquals(File.Bioformat.VARIANT, fileVcf.getBioformat());
        Job indexJobVcf = fileTest.index(fileVcf.getId(), sessionId);

        /* Emulate DAEMON working */
        indexJobVcf = runIndexJob(sessionId, indexJobVcf);
        assertEquals(Job.Status.READY, indexJobVcf.getStatus());

        QueryOptions queryOptions = new QueryOptions("limit", 10);
        queryOptions.put("region", "1");
        List<Variant> variants = fileTest.fetchVariants(fileVcf.getId(), sessionId, queryOptions);
        assertEquals(10, variants.size());


        File fileBam = fileTest.uploadBam(study.getId(), sessionId);
        assertEquals(File.Status.READY, fileBam.getStatus());
        assertEquals(File.Bioformat.ALIGNMENT, fileBam.getBioformat());
        Job indexJobBam = fileTest.index(fileBam.getId(), sessionId);

        /* Emulate DAEMON working */
        indexJobBam = runIndexJob(sessionId, indexJobBam);
        assertEquals(Job.Status.READY, indexJobBam.getStatus());

        queryOptions = new QueryOptions("limit", 10);
        queryOptions.put("region", "20:60000-60200");
        queryOptions.put(AlignmentDBAdaptor.QO_INCLUDE_COVERAGE, false);
        List<ObjectMap> alignments = fileTest.fetchAlignments(fileBam.getId(), sessionId, queryOptions);
//        assertEquals(10, alignments.size());

    }

    /**
     * Do not execute Job using its command line, won't find the opencga-storage.sh
     * Call directly to the OpenCGAStorageMain
     */
    private Job runIndexJob(String sessionId, Job indexJob) throws AnalysisExecutionException, IOException, CatalogException {
        String[] args = Commandline.translateCommandline(indexJob.getCommandLine());
        org.opencb.opencga.storage.app.StorageMain.main(Arrays.copyOfRange(args, 1, args.length));
        indexJob.setCommandLine("echo 'Executing fake CLI'");
        AnalysisJobExecutor.execute(OpenCGAWSServer.catalogManager, indexJob, sessionId);
        return OpenCGAWSServer.catalogManager.getJob(indexJob.getId(), null, sessionId).first();
    }

}
