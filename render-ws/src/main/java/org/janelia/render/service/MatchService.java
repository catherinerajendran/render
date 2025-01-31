package org.janelia.render.service;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.janelia.alignment.match.CanvasMatches;
import org.janelia.alignment.match.MatchCollectionId;
import org.janelia.alignment.match.MatchCollectionMetaData;
import org.janelia.alignment.match.MatchTrial;
import org.janelia.alignment.match.parameters.MatchTrialParameters;
import org.janelia.render.service.dao.MatchDao;
import org.janelia.render.service.model.IllegalServiceArgumentException;
import org.janelia.render.service.util.RenderServiceUtil;
import org.janelia.render.service.util.SharedImageProcessorCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * APIs for accessing point match data stored in the Match service database.
 *
 * @author Eric Trautman
 */
@Path("/")
@Api(tags = {"Point Match APIs"})
public class MatchService {

    private final MatchDao matchDao;

    @SuppressWarnings("UnusedDeclaration")
    public MatchService()
            throws UnknownHostException {
        this(MatchDao.build());
    }

    private MatchService(final MatchDao matchDao) {
        this.matchDao = matchDao;
    }

    @Path("v1/matchCollectionOwners")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List of all data owners")
    public Set<String> getOwners() {

        LOG.info("getOwners: entry");

        final List<MatchCollectionMetaData> metaDataList = matchDao.getMatchCollectionMetaData();
        final Set<String> owners = new LinkedHashSet<>(metaDataList.size());
        //noinspection Convert2streamapi
        for (final MatchCollectionMetaData metaData : metaDataList) {
            owners.add(metaData.getOwner());
        }

        return owners;
    }

    @Path("v1/owner/{owner}/matchCollections")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List of match collection metadata for the specified owner")
    public List<MatchCollectionMetaData> getMatchCollections(@PathParam("owner") final String owner) {

        LOG.info("getMatchCollections: entry, owner={}", owner);

        if (owner == null) {
            throw new IllegalArgumentException("owner must be specified");
        }

        final List<MatchCollectionMetaData> metaDataList = matchDao.getMatchCollectionMetaData();
        final List<MatchCollectionMetaData> ownerMetaDataList = new ArrayList<>(metaDataList.size());
        //noinspection Convert2streamapi
        for (final MatchCollectionMetaData metaData : metaDataList) {
            if (owner.equals(metaData.getOwner())) {
                ownerMetaDataList.add(metaData);
            }
        }

        return ownerMetaDataList;
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/pGroupIds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List distinct pGroup identifiers")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public List<String> getDistinctPGroupIds(@PathParam("owner") final String owner,
                                             @PathParam("matchCollection") final String matchCollection) {

        LOG.info("getDistinctPGroupIds: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        return matchDao.getDistinctPGroupIds(collectionId);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/multiConsensusPGroupIds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            tags = "Hierarchical APIs",
            value = "List pGroup identifiers with multiple consensus set match pairs",
            notes = "Looks for cross layer pGroupId/qGroupId combinations that have more than one match pair")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public List<String> getMultiConsensusPGroupIds(@PathParam("owner") final String owner,
                                                   @PathParam("matchCollection") final String matchCollection) {

        LOG.info("getMultiConsensusPGroupIds: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        return matchDao.getMultiConsensusPGroupIds(collectionId);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/multiConsensusGroupIds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            tags = "Hierarchical APIs",
            value = "List p and q group identifiers with multiple consensus set match pairs",
            notes = "Looks for cross layer pGroupId/qGroupId combinations that have more than one match pair")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Set<String> getMultiConsensusGroupIds(@PathParam("owner") final String owner,
                                                  @PathParam("matchCollection") final String matchCollection) {

        LOG.info("getMultiConsensusGroupIds: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        return matchDao.getMultiConsensusGroupIds(collectionId);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/qGroupIds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List distinct qGroup identifiers")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public List<String> getDistinctQGroupIds(@PathParam("owner") final String owner,
                                             @PathParam("matchCollection") final String matchCollection) {

        LOG.info("getDistinctQGroupIds: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        return matchDao.getDistinctQGroupIds(collectionId);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/groupIds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List distinct group (p and q) identifiers")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public List<String> getDistinctGroupIds(@PathParam("owner") final String owner,
                                            @PathParam("matchCollection") final String matchCollection) {

        LOG.info("getDistinctGroupIds: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        return matchDao.getDistinctGroupIds(collectionId);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/pGroup/{pGroupId}/matches")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches with the specified pGroup",
            notes = "Find all matches where the first tile is in the specified layer.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesWithPGroup(@PathParam("owner") final String owner,
                                         @PathParam("matchCollection") final String matchCollection,
                                         @PathParam("pGroupId") final String pGroupId,
                                         @DefaultValue("false") @QueryParam("excludeMatchDetails") final boolean excludeMatchDetails,
                                         @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesWithPGroup: entry, owner={}, matchCollection={}, pGroupId={}, mergeCollectionList={}",
                 owner, matchCollection, pGroupId, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesWithPGroup(collectionId, mergeCollectionIdList, pGroupId, excludeMatchDetails, output);

        return streamResponse(responseOutput);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{groupId}/matchesWithinGroup")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches within the specified group",
            notes = "Find all matches where both tiles are in the specified layer.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesWithinGroup(@PathParam("owner") final String owner,
                                          @PathParam("matchCollection") final String matchCollection,
                                          @PathParam("groupId") final String groupId,
                                          @DefaultValue("false") @QueryParam("excludeMatchDetails") final boolean excludeMatchDetails,
                                          @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesWithinGroup: entry, owner={}, matchCollection={}, groupId={}, mergeCollectionList={}",
                 owner, matchCollection, groupId, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesWithinGroup(collectionId, mergeCollectionIdList, groupId, excludeMatchDetails, output);

        return streamResponse(responseOutput);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{groupId}/matchesOutsideGroup")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches outside the specified group",
            notes = "Find all matches with one tile in the specified layer and another tile outside that layer.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesOutsideGroup(@PathParam("owner") final String owner,
                                           @PathParam("matchCollection") final String matchCollection,
                                           @PathParam("groupId") final String groupId,
                                           @DefaultValue("false") @QueryParam("excludeMatchDetails") final boolean excludeMatchDetails,
                                           @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesOutsideGroup: entry, owner={}, matchCollection={}, groupId={}, mergeCollectionList={}",
                 owner, matchCollection, groupId, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesOutsideGroup(collectionId, mergeCollectionIdList, groupId, excludeMatchDetails, output);

        return streamResponse(responseOutput);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{pGroupId}/matchesWith/{qGroupId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches between the specified groups",
            notes = "Find all matches with one tile in the specified p layer and another tile in the specified q layer.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesBetweenGroups(@PathParam("owner") final String owner,
                                            @PathParam("matchCollection") final String matchCollection,
                                            @PathParam("pGroupId") final String pGroupId,
                                            @PathParam("qGroupId") final String qGroupId,
                                            @DefaultValue("false") @QueryParam("excludeMatchDetails") final boolean excludeMatchDetails,
                                            @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesBetweenGroups: entry, owner={}, matchCollection={}, pGroupId={}, qGroupId={}, mergeCollectionList={}",
                 owner, matchCollection, pGroupId, qGroupId, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesBetweenGroups(collectionId, mergeCollectionIdList, pGroupId, qGroupId, excludeMatchDetails, output);

        return streamResponse(responseOutput);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{pGroupId}/id/{pId}/matchesWith/{qGroupId}/id/{qId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches between the specified objects",
            notes = "Find all matches between two specific tiles.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesBetweenObjects(@PathParam("owner") final String owner,
                                             @PathParam("matchCollection") final String matchCollection,
                                             @PathParam("pGroupId") final String pGroupId,
                                             @PathParam("pId") final String pId,
                                             @PathParam("qGroupId") final String qGroupId,
                                             @PathParam("qId") final String qId,
                                             @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesBetweenObjects: entry, owner={}, matchCollection={}, pGroupId={}, pId={}, qGroupId={}, qId={}, mergeCollectionList={}",
                 owner, matchCollection, pGroupId, pId, qGroupId, qId, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesBetweenObjects(collectionId, mergeCollectionIdList, pGroupId, pId, qGroupId, qId, output);

        return streamResponse(responseOutput);
    }
    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{pGroupId}/id/{pId}/matchesWith/{qGroupId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches from a specified object to a specified group",
            notes = "Find all matches between a specific tile and a specific section.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesFromObjectToGroup(@PathParam("owner") final String owner,
                                                @PathParam("matchCollection") final String matchCollection,
                                                @PathParam("pGroupId") final String pGroupId,
                                                @PathParam("pId") final String pId,
                                                @PathParam("qGroupId") final String qGroupId,
                                                @DefaultValue("false") @QueryParam("excludeMatchDetails") final boolean excludeMatchDetails,
                                                @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesFromObjectToGroup: entry, owner={}, matchCollection={}, pGroupId={}, pId={}, qGroupId={}, mergeCollectionList={}",
                 owner, matchCollection, pGroupId, pId, qGroupId, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesBetweenObjectAndGroup(collectionId, mergeCollectionIdList, pGroupId, pId, qGroupId, excludeMatchDetails, output);

        return streamResponse(responseOutput);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{groupId}/id/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find matches from or to a specific object",
            notes = "Find all matches that either come from or to a specific tile.",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchesInvolvingObject(@PathParam("owner") final String owner,
                                             @PathParam("matchCollection") final String matchCollection,
                                             @PathParam("groupId") final String groupId,
                                             @PathParam("id") final String id,
                                             @QueryParam("mergeCollection") final List<String> mergeCollectionList) {

        LOG.info("getMatchesInvolvingObject: entry, owner={}, matchCollection={}, groupId={}, id={}, mergeCollectionList={}",
                 owner, matchCollection, groupId, id, mergeCollectionList);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        final List<MatchCollectionId> mergeCollectionIdList = getCollectionIdList(owner, mergeCollectionList);
        final StreamingOutput responseOutput =
                output -> matchDao.writeMatchesInvolvingObject(collectionId, mergeCollectionIdList, groupId, id, output);

        return streamResponse(responseOutput);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{groupId}/id/{id}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Delete matches from or to a specific object",
            notes = "Delete all matches from or to a specific tile.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response deleteMatchesInvolvingObject(@PathParam("owner") final String owner,
                                                 @PathParam("matchCollection") final String matchCollection,
                                                 @PathParam("groupId") final String groupId,
                                                 @PathParam("id") final String id) {

        LOG.info("deleteMatchesInvolvingObject: entry, owner={}, matchCollection={}, groupId={}, id={}",
                 owner, matchCollection, groupId, id);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);

        Response response = null;
        try {
            matchDao.removeMatchesInvolvingObject(collectionId, groupId, id);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{pGroupId}/id/{pId}/matchesWith/{qGroupId}/id/{qId}")
    @DELETE
    @ApiOperation(
            value = "Delete matches between the specified tiles",
            notes = "Delete all matches between two specific tiles.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response deleteMatchesBetweenTiles(@PathParam("owner") final String owner,
                                              @PathParam("matchCollection") final String matchCollection,
                                              @PathParam("pGroupId") final String pGroupId,
                                              @PathParam("pId") final String pId,
                                              @PathParam("qGroupId") final String qGroupId,
                                              @PathParam("qId") final String qId) {

        LOG.info("deleteMatchesBetweenTiles: entry, owner={}, matchCollection={}, pGroupId={}, pId={}, qGroupId={}, qId={}",
                 owner, matchCollection, pGroupId, pId, qGroupId, qId);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);

        Response response = null;
        try {
            matchDao.removeMatchesBetweenTiles(collectionId, pGroupId, pId, qGroupId, qId);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{pGroupId}/matchesWith/{qGroupId}")
    @DELETE
    @ApiOperation(
            value = "Delete matches between the specified groups",
            notes = "Delete all matches between two groups.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response deleteMatchesBetweenGroups(@PathParam("owner") final String owner,
                                               @PathParam("matchCollection") final String matchCollection,
                                               @PathParam("pGroupId") final String pGroupId,
                                               @PathParam("qGroupId") final String qGroupId) {

        LOG.info("deleteMatchesBetweenGroups: entry, owner={}, matchCollection={}, pGroupId={}, qGroupId={}",
                 owner, matchCollection, pGroupId, qGroupId);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);

        Response response = null;
        try {
            matchDao.removeMatchesBetweenGroups(collectionId, pGroupId, qGroupId);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/pGroup/{pGroupId}/matches")
    @DELETE
    @ApiOperation(
            value = "Delete matches with the specified p group")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response deleteMatchesWithPGroup(@PathParam("owner") final String owner,
                                            @PathParam("matchCollection") final String matchCollection,
                                            @PathParam("pGroupId") final String pGroupId) {

        LOG.info("deleteMatchesWithPGroup: entry, owner={}, matchCollection={}, pGroupId={}",
                 owner, matchCollection, pGroupId);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);

        Response response = null;
        try {
            matchDao.removeMatchesWithPGroup(collectionId, pGroupId);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/group/{groupId}/matchesOutsideGroup")
    @DELETE
    @ApiOperation(
            value = "Delete matches outside the specified group",
            notes = "Delete all matches with one tile in the specified layer and another tile outside that layer.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response deleteMatchesOutsideGroup(@PathParam("owner") final String owner,
                                              @PathParam("matchCollection") final String matchCollection,
                                              @PathParam("groupId") final String groupId) {

        LOG.info("deleteMatchesOutsideGroup: entry, owner={}, matchCollection={}, groupId={}",
                 owner, matchCollection, groupId);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        Response response = null;
        try {
            matchDao.removeMatchesOutsideGroup(collectionId, groupId);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
     }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/matches")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Save a set of matches",
            notes = "Inserts or updates matches for the specified collection.")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "matches successfully saved"),
            @ApiResponse(code = 400, message = "If no matches are provided")
    })
    public Response saveMatches(@PathParam("owner") final String owner,
                                @PathParam("matchCollection") final String matchCollection,
                                @Context final UriInfo uriInfo,
                                final List<CanvasMatches> canvasMatchesList) {

        LOG.info("saveMatches: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);

        if (canvasMatchesList == null) {
            throw new IllegalServiceArgumentException("no matches provided");
        }

        try {
            matchDao.saveMatches(collectionId, canvasMatchesList);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        LOG.info("saveMatches: exit");

        return responseBuilder.build();
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/pGroup/{pGroupId}/matchCounts")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Find match counts for all pairs with the specified pGroup",
            response = CanvasMatches.class,
            responseContainer="List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response getMatchCountsForPGroup(@PathParam("owner") final String owner,
                                            @PathParam("matchCollection") final String matchCollection,
                                            @PathParam("pGroupId") final String pGroupId) {

        return getMatchesWithPGroup(owner, matchCollection, pGroupId,true, null);
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}/pGroup/{pGroupId}/matchCounts")
    @PUT
    @ApiOperation(
            value = "Update match counts for all pairs with specified pGroup")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "match counts successfully updated"),
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response updateMatchCountsForPGroup(@PathParam("owner") final String owner,
                                               @PathParam("matchCollection") final String matchCollection,
                                               @PathParam("pGroupId") final String pGroupId,
                                               @Context final UriInfo uriInfo) {

        LOG.info("updateMatchCountsForPGroup: entry, owner={}, matchCollection={}, pGroupId={}",
                 owner, matchCollection, pGroupId);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        try {
            matchDao.updateMatchCountsForPGroup(collectionId, pGroupId);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        LOG.info("updateMatchCountsForPGroup: exit, pGroupId={}", pGroupId);

        return responseBuilder.build();
    }

    @Path("v1/owner/{owner}/matchCollection/{matchCollection}")
    @DELETE
    @ApiOperation(
            value = "Delete the collection",
            notes = "Use this wisely.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Match collection not found")
    })
    public Response deleteAllMatches(@PathParam("owner") final String owner,
                                     @PathParam("matchCollection") final String matchCollection) {

        LOG.info("deleteAllMatches: entry, owner={}, matchCollection={}",
                 owner, matchCollection);

        final MatchCollectionId collectionId = getCollectionId(owner, matchCollection);
        Response response = null;
        try {
            matchDao.removeAllMatches(collectionId);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    @Path("v1/owner/{owner}/matchCollection/{fromMatchCollection}/collectionId")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Rename a match collection")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "stack successfully renamed"),
            @ApiResponse(code = 400, message = "stack cannot be renamed"),
            @ApiResponse(code = 404, message = "stack not found")
    })
    public Response renameMatchCollection(@PathParam("owner") final String owner,
                                          @PathParam("fromMatchCollection") final String fromMatchCollection,
                                          @Context final UriInfo uriInfo,
                                          final MatchCollectionId toCollectionId) {

        LOG.info("renameMatchCollection: entry, owner={}, fromMatchCollection={}, toCollectionId={}",
                 owner, fromMatchCollection, toCollectionId);

        final MatchCollectionId fromCollectionId = getCollectionId(owner, fromMatchCollection);
        try {
            matchDao.renameMatchCollection(fromCollectionId, toCollectionId);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return Response.ok().build();
    }

    @Path("v1/owner/{owner}/matchTrial/{trialId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get match trial")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "trial not found")
    })
    public MatchTrial getMatchTrial(@PathParam("owner") final String owner,
                                    @PathParam("trialId") final String trialId) {

        LOG.info("getMatchTrial: entry, owner={}, trialId={}", owner, trialId);

        MatchTrial matchTrial = null;
        try {
            matchTrial = matchDao.getMatchTrial(trialId);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return matchTrial;
    }

    @Path("v1/owner/{owner}/matchTrial")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Create a match trial",
            notes = "Derives matches for the specified canvas pair.")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Trial results have been saved"),
            @ApiResponse(code = 400, message = "If no matches are provided")
    })
    public MatchTrial runMatchTrial(@PathParam("owner") final String owner,
                                    final MatchTrialParameters matchTrialParameters) {

        LOG.info("runMatchTrial: entry, owner={}", owner);

        MatchTrial matchTrial = null;
        try {

            if (matchTrialParameters == null) {
                throw new IllegalArgumentException("no parameters provided");
            } else {
                matchTrialParameters.validateAndSetDefaults();
            }

            matchTrial = new MatchTrial(matchTrialParameters);
            matchTrial.deriveResults(SharedImageProcessorCache.getInstance());
            matchTrial = matchDao.insertMatchTrial(matchTrial);

            LOG.info("runMatchTrial: exit, saved trial {}", matchTrial.getId());

        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return matchTrial;
    }

    @Path("v1/owner/{owner}/matchTrial/{trialId}")
    @DELETE
    @ApiOperation(
            value = "Delete match trial")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Trial removed (or was not found)")
    })
    public Response deleteMatchTrial(@PathParam("owner") final String owner,
                                     @PathParam("trialId") final String trialId) {

        LOG.info("deleteMatchTrial: entry, owner={}, trialId={}", owner, trialId);

        Response response = null;
        try {
            matchDao.removeMatchTrial(trialId);
            response = Response.ok().build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    static MatchCollectionId getCollectionId(final String owner,
                                             final String matchCollection) {

        MatchCollectionId collectionId = null;
        try {
            collectionId = new MatchCollectionId(owner, matchCollection);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return collectionId;
    }

    private List<MatchCollectionId> getCollectionIdList(final String owner,
                                                        final List<String> matchCollectionList) {
        List<MatchCollectionId> collectionIdList = null;
        if (matchCollectionList != null) {
            collectionIdList = new ArrayList<>(matchCollectionList.size());
            for (final String matchCollection : matchCollectionList) {
                collectionIdList.add(getCollectionId(owner, matchCollection));
            }
        }
        return collectionIdList;
    }

    private Response streamResponse(final StreamingOutput responseOutput) {

        Response response = null;
        try {
            response = Response.ok(responseOutput).build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    private static final Logger LOG = LoggerFactory.getLogger(MatchService.class);

}
