package org.opencb.opencga.catalog.core.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opencb.commons.test.GenericTest;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.core.beans.*;
import org.opencb.opencga.lib.auth.IllegalOpenCGACredentialsException;
import org.opencb.opencga.lib.auth.MongoCredentials;
import org.opencb.opencga.lib.common.TimeUtils;

import java.io.IOException;

import static org.junit.Assert.*;

public class CatalogMongoDBAdaptorTest extends GenericTest {

    public static final String ID_LOGIN_TEST = "ID_LOGIN_TEST";
    CatalogMongoDBAdaptor catalog;

    @Before
    public void before() throws IllegalOpenCGACredentialsException, JsonProcessingException {

        MongoCredentials mongoCredentials = new MongoCredentials("localhost", 27017, "catalog", "", "");
        catalog = new CatalogMongoDBAdaptor(mongoCredentials);
        catalog.connect();

    }

    @After
    public void after(){
        catalog.disconnect();
    }

    @Test
    public void createUserTest() throws CatalogManagerException, JsonProcessingException {
        User userInvalid = new User("jcoll", "jacobo", "", "asdf", "", "", "");
        QueryResult createUser = catalog.createUser(userInvalid);
        System.out.println(createUser.toString());

        User user = new User("jmmut", "Jose Miguel", "jmmut@ebi", "1111", "ACME", "no", "off");
        QueryResult createUser2 = catalog.createUser(user);
        System.out.println(createUser2.toString());
    }

    @Test
    public void loginTest() throws CatalogManagerException, IOException {
        Session session = new Session("127.0.0.1");
        Session session2 = new Session("127.0.0.1"); session2.setId(ID_LOGIN_TEST);
        System.out.println(catalog.login("jcoll", "INVALID_PASSWORD", session));
        System.out.println(catalog.login("jcoll", "asdf", session));
        System.out.println(catalog.login("jcoll", "asdf", session2));

    }

    @Test
    public void changePasswordTest() throws CatalogManagerException {
        System.out.println(catalog.changePassword("jcoll", ID_LOGIN_TEST, "asdf", "ASDF"));
        //System.out.println(catalog.changePassword("jcoll", ID_LOGIN_TEST, "ASDF", "asdf"));
        System.out.println(catalog.changePassword("jcoll", ID_LOGIN_TEST, "IOQUESE", "asdf"));
    }

    @Test
    public void createProjectTest() throws CatalogManagerException, JsonProcessingException {
        Project p = new Project("Project about some genomes", "1000G", "Today", "Cool", "", "", 1000, "");
        System.out.println(catalog.createProject("jcoll", p, ID_LOGIN_TEST));
    }

    @Test
    public void getProjectIdTest(){
        System.out.println(catalog.getProjectId("jcoll", "1000G"));
        System.out.println(catalog.getProjectId("jcoll", "2000G"));
    }

    @Test
    public void getAllProjects() throws CatalogManagerException {
        System.out.println(catalog.getAllProjects("jcoll", ID_LOGIN_TEST));
    }

    @Test
    public void createStudyTest() throws CatalogManagerException, JsonProcessingException {
        Study s = new Study("Phase 1", "ph1", "TEST", "", "");
        System.out.println(catalog.createStudy("jcoll", "1000G", s, ID_LOGIN_TEST));
        s = new Study("Phase 3", "ph3", "TEST", "", "");
        System.out.println(catalog.createStudy("jcoll", "1000G", s, ID_LOGIN_TEST));
    }

    @Test
    public void getStudyIdTest(){
        System.out.println(catalog.getStudyId("jcoll", "1000G", "ph3"));
    }

    @Test
    public void createFileToStudyTest() throws CatalogManagerException, JsonProcessingException {
        File f = new File("file.bam", "t", "f", "bam", "/tmp/small.sam", null, TimeUtils.getTime(), "", "2", 1000, -1, -1);
        System.out.println(catalog.createFileToStudy("jcoll", "1000G", "ph1", f, ID_LOGIN_TEST));
    }
}