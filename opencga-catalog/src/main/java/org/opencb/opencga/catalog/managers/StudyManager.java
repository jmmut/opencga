package org.opencb.opencga.catalog.managers;

import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.authentication.AuthenticationManager;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.catalog.managers.api.IStudyManager;
import org.opencb.opencga.catalog.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.db.api.*;
import org.opencb.opencga.catalog.io.CatalogIOManager;
import org.opencb.opencga.catalog.exceptions.CatalogIOException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.core.common.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class StudyManager extends AbstractManager implements IStudyManager{

    protected static Logger logger = LoggerFactory.getLogger(StudyManager.class);

    public StudyManager(AuthorizationManager authorizationManager, AuthenticationManager authenticationManager,
                        CatalogDBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                        Properties catalogProperties) {
        super(authorizationManager, authenticationManager, catalogDBAdaptorFactory, ioManagerFactory, catalogProperties);
    }

    @Override
    public String getUserId(int studyId) throws CatalogException {
        return studyDBAdaptor.getStudyOwnerId(studyId);
    }

    @Override
    public Integer getProjectId(int studyId) throws CatalogException {
        return studyDBAdaptor.getProjectIdByStudyId(studyId);
    }

    @Override
    public Integer getStudyId(String studyId) throws CatalogException {
        try {
            return Integer.parseInt(studyId);
        } catch (NumberFormatException ignore) {
        }

        String[] split = studyId.split("@");
        if (split.length != 2) {
            return -1;
        }
        String[] projectStudy = split[1].replace(':', '/').split("/", 2);
        if (projectStudy.length != 2) {
            return -2;
        }
        int projectId = userDBAdaptor.getProjectId(split[0], projectStudy[0]);
        return studyDBAdaptor.getStudyId(projectId, projectStudy[1]);
    }

    @Override
    public QueryResult<Study> create(int projectId, String name, String alias, Study.Type type, String creatorId,
                                     String creationDate, String description, String status, String cipher,
                                     String uriScheme, URI uri, Map<File.Bioformat, DataStore> datastores,
                                     Map<String, Object> stats, Map<String, Object> attributes, QueryOptions options,
                                     String sessionId)
            throws CatalogException {

        ParamUtils.checkParameter(name, "name");
        ParamUtils.checkParameter(alias, "alias");
        ParamUtils.checkObj(type, "type");
        ParamUtils.checkAlias(alias, "alias");
        ParamUtils.checkParameter(sessionId, "sessionId");

        String userId = userDBAdaptor.getUserIdBySessionId(sessionId);
        description = ParamUtils.defaultString(description, "");
        creatorId = ParamUtils.defaultString(creatorId, userId);
        creationDate = ParamUtils.defaultString(creationDate, TimeUtils.getTime());
        status = ParamUtils.defaultString(status, "active");
        cipher = ParamUtils.defaultString(cipher, "none");
        if (uri != null) {
            if (uri.getScheme() == null) {
                throw new CatalogException("StudyUri must specify the scheme");
            } else {
                if (uriScheme != null && !uriScheme.isEmpty()) {
                    if (!uriScheme.equals(uri.getScheme())) {
                        throw new CatalogException("StudyUri must specify the scheme");
                    }
                } else {
                    uriScheme = uri.getScheme();
                }
            }
        } else {
            uriScheme = catalogIOManagerFactory.getDefaultCatalogScheme();
        }
        datastores = ParamUtils.defaultObject(datastores, HashMap<File.Bioformat, DataStore>::new);
        stats = ParamUtils.defaultObject(stats, HashMap<String, Object>::new);
        attributes = ParamUtils.defaultObject(attributes, HashMap<String, Object>::new);

        CatalogIOManager catalogIOManager = catalogIOManagerFactory.get(uriScheme);

        String projectOwnerId = userDBAdaptor.getProjectOwnerId(projectId);


        /* Check project permissions */
        if (!authorizationManager.getProjectACL(userId, projectId).isWrite()) { //User can't write/modify the project
            throw CatalogAuthorizationException.denny(userId, "create", "Project", projectId, null);
        }
        if (!creatorId.equals(userId)) {
            if (!authorizationManager.getUserRole(userId).equals(User.Role.ADMIN)) {
                throw new CatalogException("Permission denied. Required ROLE_ADMIN to create a study with creatorId != userId");
            } else {
                if (!userDBAdaptor.userExists(creatorId)) {
                    throw new CatalogException("ERROR: CreatorId does not exist.");
                }
            }
        }

//        URI projectUri = catalogIOManager.getProjectUri(projectOwnerId, Integer.toString(projectId));
        LinkedList<File> files = new LinkedList<>();
        LinkedList<Experiment> experiments = new LinkedList<>();
        LinkedList<Job> jobs = new LinkedList<>();
        LinkedList<Acl> acls = new LinkedList<>();

        /* Add default ACL */
        if (!creatorId.equals(projectOwnerId)) {
            //Add full permissions for the creator if he is not the owner
            acls.add(new Acl(creatorId, true, true, true, true));
        }

        //Copy generic permissions from the project.

        QueryResult<Acl> aclQueryResult = userDBAdaptor.getProjectAcl(projectId, Acl.USER_OTHERS_ID);
        if (!aclQueryResult.getResult().isEmpty()) {
            //study.getAcl().add(aclQueryResult.getResult().get(0));
        } else {
            throw new CatalogDBException("Project " + projectId + " must have generic ACL");
        }


        File rootFile = new File(".", File.Type.FOLDER, null, null, "", creatorId, "study root folder", File.Status.READY, 0);
        rootFile.setUri(uri);
        files.add(rootFile);

        Study study = new Study(-1, name, alias, type, creatorId, creationDate, description, status, TimeUtils.getTime(),
                0, cipher, acls, experiments, files, jobs, new LinkedList<Sample>(), new LinkedList<Dataset>(),
                new LinkedList<Cohort>(), new LinkedList<VariableSet>(), null, datastores, stats, attributes);


        /* CreateStudy */
        QueryResult<Study> result = studyDBAdaptor.createStudy(projectId, study, options);
        study = result.getResult().get(0);

        //URI studyUri;
        if (uri == null) {
            try {
                uri = catalogIOManager.createStudy(projectOwnerId, Integer.toString(projectId), Integer.toString(study.getId()));
            } catch (CatalogIOException e) {
                e.printStackTrace();
                studyDBAdaptor.deleteStudy(study.getId());
                throw e;
            }
        }

        studyDBAdaptor.modifyStudy(study.getId(), new ObjectMap("uri", uri));
        int rootFileId = fileDBAdaptor.getFileId(study.getId(), "");    //Set studyUri to the root folder too
        fileDBAdaptor.modifyFile(rootFileId, new ObjectMap("uri", uri));

        userDBAdaptor.updateUserLastActivity(projectOwnerId);
        return result;
    }

    @Override
    public QueryResult<Study> share(int studyId, Acl acl) throws CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResult<Study> create(QueryOptions params, String sessionId) throws CatalogException {
        ParamUtils.checkObj(params, "QueryOptions");
        return create(
                params.getInt("projectId", -1),
                params.getString("name"),
                params.getString("alias"),
                Study.Type.valueOf(params.getString("type", Study.Type.CASE_CONTROL.toString())),
                params.getString("creatorId"),
                params.getString("creationDate"),
                params.getString("description"),
                params.getString("status"),
                params.getString("cipher"),
                params.getString("uriScheme"),
                params.get("uri", URI.class, null),
                params.get("datastores", Map.class, null),
                params.getMap("stats"),
                params.getMap("attributes"),
                params, sessionId
        );
    }

    @Override
    public QueryResult<Study> read(Integer studyId, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(sessionId, "sessionId");
        String userId = userDBAdaptor.getUserIdBySessionId(sessionId);
        Acl studyAcl = authorizationManager.getStudyACL(userId, studyId);
        if (studyAcl.isRead()) {
            QueryResult<Study> studyResult = studyDBAdaptor.getStudy(studyId, options);
            if (!studyResult.getResult().isEmpty()) {
                authorizationManager.filterFiles(userId, studyAcl, studyResult.getResult().get(0).getFiles());
            }
            return studyResult;
        } else {
            throw new CatalogDBException("Permission denied. Can't read this study");
        }
    }

    @Override
    public QueryResult<Study> readAll(QueryOptions query, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(sessionId, "sessionId");
        int projectId = query.getInt("projectId", -1);
        ParamUtils.checkId(projectId, "ProjectId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        String userId = userDBAdaptor.getUserIdBySessionId(sessionId);


        Acl projectAcl = authorizationManager.getProjectACL(userId, projectId);
        if (!projectAcl.isRead()) {
            throw new CatalogDBException("Permission denied. Can't read project");
        }

        QueryResult<Study> allStudies = studyDBAdaptor.getAllStudies(projectId, options);
        List<Study> studies = allStudies.getResult();
        authorizationManager.filterStudies(userId, projectAcl, studies);
        allStudies.setResult(studies);

        return allStudies;
    }

    @Override
    public QueryResult<Study> update(Integer studyId, ObjectMap parameters, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(parameters, "Parameters");
        ParamUtils.checkParameter(sessionId, "sessionId");
        String userId = userDBAdaptor.getUserIdBySessionId(sessionId);
        if (!authorizationManager.getStudyACL(userId, studyId).isWrite()) {
            throw new CatalogDBException("User " + userId + " can't modify the study " + studyId);
        }
        if (parameters.containsKey("alias")) {
            rename(studyId, parameters.getString("alias"), sessionId);

            //Clone and remove alias from parameters. Do not modify the original parameter
            parameters = new ObjectMap(parameters);
            parameters.remove("alias");
        }
        for (String s : parameters.keySet()) {
            if (!s.matches("name|type|description|status|attributes|stats")) {
                throw new CatalogDBException("Parameter '" + s + "' can't be changed");
            }
        }

        String ownerId = studyDBAdaptor.getStudyOwnerId(studyId);
        userDBAdaptor.updateUserLastActivity(ownerId);
        QueryResult<ObjectMap> result = studyDBAdaptor.modifyStudy(studyId, parameters);
        return new QueryResult<>(result.getId(), result.getDbTime(), result.getNumResults(),
                result.getNumTotalResults(), result.getWarningMsg(), result.getErrorMsg(),
                read(studyId, options, sessionId).getResult());
    }

    private QueryResult rename(int studyId, String newStudyAlias, String sessionId)
            throws CatalogException {
        ParamUtils.checkAlias(newStudyAlias, "newStudyAlias");
        ParamUtils.checkParameter(sessionId, "sessionId");
        String sessionUserId = userDBAdaptor.getUserIdBySessionId(sessionId);
        String studyOwnerId = studyDBAdaptor.getStudyOwnerId(studyId);

        if (!authorizationManager.getStudyACL(sessionUserId, studyId).isWrite()) {  //User can't write/modify the study
            throw new CatalogDBException("Permission denied. Can't write in project");
        }

        // Both users must bu updated
        userDBAdaptor.updateUserLastActivity(sessionUserId);
        userDBAdaptor.updateUserLastActivity(studyOwnerId);
        //TODO get all shared users to updateUserLastActivity

        return studyDBAdaptor.renameStudy(studyId, newStudyAlias);

    }


    @Override
    public QueryResult<Study> delete(Integer id, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException();
    }
}
