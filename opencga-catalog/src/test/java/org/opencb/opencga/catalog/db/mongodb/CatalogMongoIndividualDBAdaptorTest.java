package org.opencb.opencga.catalog.db.mongodb;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.db.api.CatalogDBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.CatalogIndividualDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.Individual;
import org.opencb.opencga.catalog.models.Sample;
import org.opencb.opencga.catalog.models.User;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by hpccoll1 on 19/06/15.
 */
public class CatalogMongoIndividualDBAdaptorTest {

    private CatalogDBAdaptorFactory dbAdaptorFactory;
    private CatalogIndividualDBAdaptor catalogIndividualDBAdaptor;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private User user1;
    private User user2;
    private User user3;

    @Before
    public void before () throws IOException, CatalogDBException {
        CatalogMongoDBAdaptorTest dbAdaptorTest = new CatalogMongoDBAdaptorTest();
        dbAdaptorTest.before();

        user1 = CatalogMongoDBAdaptorTest.user1;
        user2 = CatalogMongoDBAdaptorTest.user2;
        user3 = CatalogMongoDBAdaptorTest.user3;
        dbAdaptorFactory = CatalogMongoDBAdaptorTest.catalogDBAdaptor;
        catalogIndividualDBAdaptor = dbAdaptorFactory.getCatalogIndividualDBAdaptor();

    }

    @AfterClass
    public static void afterClass() {
        CatalogMongoDBAdaptorTest.afterClass();
    }

    @Test
    public void testCreateIndividual() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(), null);
    }

    @Test
    public void testCreateIndividualStudyNotFound() throws Exception {
        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.createIndividual(-10, new Individual(), null);
    }

    @Test
    public void testCreateIndividualFatherNotFound() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 10, -1, "", null, "", null, null, null), null);
    }
    @Test
    public void testCreateIndividualAlreadyExists() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", -1, -1, "", null, "", null, null, null), null);
        thrown.expect(CatalogDBException.class); //Name already exists
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", -1, -1, "", null, "", null, null, null), null);
    }

    @Test
    public void testGetIndividual() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        Individual individual = new Individual(0, "An Individual", -1, -1, "Family", Individual.Gender.MALE, "", null, new Individual.Population(), null);
        individual = catalogIndividualDBAdaptor.createIndividual(studyId, individual, null).first();
        Individual individual2 = catalogIndividualDBAdaptor.getIndividual(individual.getId(), null).first();
        assertEquals(individual, individual2);
    }

    @Test
    public void testGetIndividualNoExists() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        Individual individual = new Individual(0, "An Individual", -1, -1, "Family", Individual.Gender.MALE, "", null, new Individual.Population(), null);
        catalogIndividualDBAdaptor.createIndividual(studyId, individual, null).first();
        catalogIndividualDBAdaptor.getIndividual(individual.getId(), null).first();
        thrown.expect(CatalogDBException.class); //Id not found
        catalogIndividualDBAdaptor.getIndividual(9999, null);
    }

    @Test
    public void testGetAllIndividuals() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "ind_1", -1, -1, "Family1", Individual.Gender.MALE, "", null, new Individual.Population(), null), null);
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "ind_2", -1, -1, "Family1", Individual.Gender.FEMALE, "", null, new Individual.Population(), null), null);
        int father = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "ind_3", -1, -1, "Family2", Individual.Gender.MALE, "", null, new Individual.Population(), null), null).first().getId();
        int mother = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "ind_4", -1, -1, "Family2", Individual.Gender.FEMALE, "", null, new Individual.Population(), null), null).first().getId();
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "ind_5", father, mother, "Family2", Individual.Gender.MALE, "", null, new Individual.Population(), null), null);
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "ind_6", -1, -1, "Family3", Individual.Gender.FEMALE, "", null, new Individual.Population(), null), null);

        QueryResult<Individual> result;
        result = catalogIndividualDBAdaptor.getAllIndividuals(studyId, new QueryOptions(CatalogIndividualDBAdaptor.IndividualFilterOption.name.toString(), "~ind_[1-3]"));
        assertEquals(3, result.getNumResults());

        result = catalogIndividualDBAdaptor.getAllIndividuals(studyId, new QueryOptions(CatalogIndividualDBAdaptor.IndividualFilterOption.gender.toString(), Individual.Gender.FEMALE));
        assertEquals(3, result.getNumResults());

        result = catalogIndividualDBAdaptor.getAllIndividuals(studyId, new QueryOptions(CatalogIndividualDBAdaptor.IndividualFilterOption.family.toString(), "Family2"));
        assertEquals(3, result.getNumResults());

        result = catalogIndividualDBAdaptor.getAllIndividuals(studyId, new QueryOptions(CatalogIndividualDBAdaptor.IndividualFilterOption.fatherId.toString(), ">0"));
        assertEquals(1, result.getNumResults());
    }

    @Test
    public void testModifyIndividual() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();

        QueryOptions params = new QueryOptions("family", "new Family");
        params.add("gender", "MALE");
        catalogIndividualDBAdaptor.modifyIndividual(individualId, params);
        Individual individual = catalogIndividualDBAdaptor.getIndividual(individualId, null).first();
        assertEquals("new Family", individual.getFamily());
        assertEquals(Individual.Gender.MALE, individual.getGender());

    }

    @Test
    public void testModifyIndividualBadGender() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();

        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.modifyIndividual(individualId, new QueryOptions("gender", "bad gender"));
    }

    @Test
    public void testModifyIndividualBadFatherId() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();

        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.modifyIndividual(individualId, new QueryOptions("fatherId", -4));
    }

    @Test
    public void testModifyIndividualExistingName() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();
        catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in2", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();

        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.modifyIndividual(individualId, new QueryOptions("name", "in2"));
    }

    @Test
    public void testDeleteIndividual() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();
        int individualId2 = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in2", 0, individualId, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();

        catalogIndividualDBAdaptor.deleteIndividual(individualId2, null);
        catalogIndividualDBAdaptor.deleteIndividual(individualId, null);

    }

    @Test
    public void testDeleteIndividualInUse() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();
        int individualId2 = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in2", 0, individualId, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();

        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.deleteIndividual(individualId, null);

    }

    @Test
    public void testDeleteIndividualInUseAsSample() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(0, "in1", 0, 0, "", Individual.Gender.UNKNOWN, "", null, null, null), null).first().getId();
        dbAdaptorFactory.getCatalogSampleDBAdaptor().createSample(studyId, new Sample(0, "Sample", "", individualId, ""), null);

        thrown.expect(CatalogDBException.class);
        catalogIndividualDBAdaptor.deleteIndividual(individualId, null);

    }

    @Test
    public void testGetStudyIdByIndividualId() throws Exception {
        int studyId = user3.getProjects().get(0).getStudies().get(0).getId();
        int individualId = catalogIndividualDBAdaptor.createIndividual(studyId, new Individual(), null).first().getId();
        int studyIdByIndividualId = catalogIndividualDBAdaptor.getStudyIdByIndividualId(individualId);
        assertEquals(studyId, studyIdByIndividualId);
    }

}