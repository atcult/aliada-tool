// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-rdfizer
// Responsible: ALIADA Consortiums
package eu.aliada.rdfizer.rest;

import static java.nio.file.StandardCopyOption.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.eclipse.jetty.client.ResponseNotifier;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcWriter;
import org.marc4j.MarcXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.aliada.rdfizer.datasource.Cache;
import eu.aliada.rdfizer.datasource.rdbms.JobInstance;
import eu.aliada.rdfizer.datasource.rdbms.JobInstanceRepository;
import eu.aliada.rdfizer.datasource.rdbms.JobStats;
import eu.aliada.rdfizer.datasource.rdbms.JobStatsRepository;
import eu.aliada.rdfizer.datasource.rdbms.ValidationMessageRepository;
import eu.aliada.rdfizer.delta.Delta;
import eu.aliada.rdfizer.delta.ModsConv;
import eu.aliada.rdfizer.log.MessageCatalog;
import eu.aliada.rdfizer.mx.InMemoryJobResourceRegistry;
import eu.aliada.rdfizer.mx.ManagementRegistrar;
import eu.aliada.rdfizer.mx.RDFizer;
import eu.aliada.rdfizer.rest.sto.Jobs;
import eu.aliada.shared.log.Log;

/**
 * RDF-izer REST resource representation.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
@Singleton
@Component
@Path("/")
public class RDFizerResource implements RDFizer {
	
	private static final Log LOGGER = new Log(RDFizerResource.class);

	@Context
	private UriInfo uriInfo;
	
	@Value(value = "${marcxml.input.dir}")
	protected String marcXmlInputDir;
	
	@Value(value = "${lido.input.dir}")
	protected String lidoInputDir;
	
	@Value(value = "${dc.input.dir}")
	protected String dcXmlInputDir;
	
	@Value(value = "${unimarcxml.input.dir}")
	protected String unimarcXmlInputDir;	
	
	@Value(value = "${auth.input.dir}")
	protected String authXmlInputDir;	
	
	@Value(value = "${record.delete.file}")
	protected String deleteRecordFile;
	
	@Value(value = "${record.delete.dir}")
	protected String deleteRecordDir;
	
	
	@Autowired
	protected Cache cache;

	@Autowired
	protected InMemoryJobResourceRegistry jobRegistry;

	@Autowired
	protected JobInstanceRepository jobInstanceRepository;

	@Autowired
	protected ValidationMessageRepository validationMessageRepository;
	
	@Autowired
	protected JobStatsRepository jobStatsRepository;
	
	@Autowired
	protected Delta delta;
	
	@Autowired
	protected ModsConv mods;
	
	protected boolean enabled = true;
	
	protected AtomicInteger runningJobCount = new AtomicInteger();
	
	/**
	 * A simple ping / I'm alive service.
	 * 
	 * @return a string containing information about the module itself.
	 */
	@GET
	@Path("/ping")
	@Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        return "ALIADA Project (UNIMARC Fork) -- RDFizer V2.0";
    }
	
	
	
	/**
	 * mods converter	
	 */
	@GET
	@Path("/mods")
	@Produces(MediaType.TEXT_PLAIN)
    public String mods() {
		mods.convert();
        return "ok";
    }
				
	/**
	 * Delete all info connected to the cluster list
	 *  { "clusters": [
     * 		{"id": "3522", "type": "NAME"},
     * 		{"id": "3436", "type": "TITLE"} ]  }  
     *  
	 * type must be NAME or TITLE
	 */	
	@POST
	@Path("/deleteCluster")
	@RequestMapping(value = "/deleteCluster", method = RequestMethod.POST)
	@ResponseBody
	public String deleteCluster(@RequestBody String json) {
		LOGGER.debug("deleteCluster called");		
		boolean result = delta.deleteCluster(json);	
		if(result) {
			return String.valueOf(HttpStatus.OK);
		}
		else {
			return String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR);
		}		
	}
	
	
	/**
	 * update info connected to the cluster list
	 *  { "clusters": [
     * 		{"id": "3522", "type": "NAME"},
     * 		{"id": "3436", "type": "TITLE"} ]  }  
     *  
	 * type must be NAME or TITLE
	 */	
	@POST
	@Path("/updateCluster")
	@RequestMapping(value = "/updateCluster", method = RequestMethod.POST)
	@ResponseBody
	public String updateCluster(@RequestBody String json) {
		LOGGER.debug("updateCluster called");		
		boolean result = delta.updateCluster(json);	
		if(result) {
			return String.valueOf(HttpStatus.OK);
		}
		else {
			return String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR);
		}		
	}
	
	/**
	 * Delete all info connected to the record list	
	 * 
	 */	
	@GET
	@Path("/deleteRecord")
	@RequestMapping(value = "/deleteRecord", method = RequestMethod.GET)
	@Produces(MediaType.TEXT_PLAIN)
	public Response deleteRecord() {				
		LOGGER.debug("deleteRecord called");	
		String result = delta.deleteRecord(deleteRecordFile);		
		
			//rename file
			try {
				java.nio.file.Path source = Paths.get(deleteRecordDir + "/" + deleteRecordFile);
				Date date = new Date();
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss") ;
				Files.move(source, source.resolveSibling("delete_record_" + dateFormat.format(date) + ".txt" ));
			} catch (Exception e){	
				e.printStackTrace();
			}
		return Response.ok(result.toString()).build();
		
	}
	

	/**
	 * delete all info connected to the record list	for udpate 
	 * 
	 */	
	@GET
	@Path("/deleteRecForUpdate")
	@RequestMapping(value = "/deleteRecForUpdate", method = RequestMethod.GET)
	@Produces(MediaType.TEXT_PLAIN)
	public Response deleteRecForUpdate() {		
		LOGGER.debug("deleteRecForUpdate called");		
		StringBuffer result = new StringBuffer();
		result.append(delta.deleteRecForUpdate());		
		return Response.ok(result.toString()).build();
	}
	
	
	
	
	@PUT
	@Path("/enable")
	@Override
	public void enable() {
		enabled = true;
	}

	@POST
	@Path("/disable")
	@Override
	public void disable() {
		enabled = false;
	}

	/**
	 * Returns a short summary of all jobs managed by this RDFizer instance.
	 * Jobs are divided by status: running and completed.
	 * For each job a minimal set of information are returned, if caller wants to know more about 
	 * a specific job then a call to {@link #getJob(Integer)} must be issued.
	 * 
	 * @return a short summary of all jobs managed by this RDFizer instance.
	 */
	@GET
	@Path("/jobs")
	@Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
	public Response getJobs() {
		return Response.ok()
				.entity(
						new Jobs(
								jobInstanceRepository.findAll(
										new Sort(
												Sort.Direction.DESC, 
												"startDate"))))
												.build();
	}
	
	/**
	 * Returns a detailed summary of the job associated with the given identifier.
	 * 
	 * @param id the job identifier.
	 * @return a detailed summary of the job associated with the given identifier.
	 */
	@GET
	@Path("/jobs/{jobid}")
	@Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
	public Response getJob(@PathParam("jobid") final Integer id) {
		final JobInstance instance = cache.getJobInstance(id);
		if (instance == null) {
			LOGGER.error(MessageCatalog._00032_JOB_INSTANCE_NOT_FOUND, id);
			return Response.status(Status.NOT_FOUND).build();											
		}
		
		final ObjectName objectName = ManagementRegistrar.createJobObjectName(instance.getFormat(), id);
		if (ManagementRegistrar.isAlreadyRegistered(objectName)) {
			final JobResource resource = jobRegistry.getJobResource(id);
			return Response.ok().entity(resource).build();
		} else {
			final JobStats stats = jobStatsRepository.findOne(id);
			if (stats == null) {
				return Response.status(Status.NOT_FOUND).build();											
			}
			stats.setInstance(instance);
			
			stats.setValidationMessages(validationMessageRepository.findByJobId(id));
			return Response.ok().entity(stats).build();
		}
	}
	
	@POST
	@Path("/newJob")
	@Consumes("application/octet-stream")
	public Response newJobFromStream(final InputStream stream) {
		final File file = new File("/tmp/data-"+System.currentTimeMillis());
		
		MarcReader reader = null;
		MarcWriter writer = null;
		try {
			reader = new MarcStreamReader(stream, "UTF-8");
			writer = new MarcXmlWriter(new FileOutputStream(file));
			int count = 0;
			if (reader.hasNext()) {
				writer.write(reader.next());
				count++;
			}
			
			if (writer != null) {
				try { writer.close();} catch (Exception ex) {}
			}
			
			if (count == 0) {
				return Response.status(Status.NO_CONTENT).build();	
			}
			
			final JobInstance configuration = cache.getJobInstance(4321);
			if (configuration == null) {
				final JobInstance instance = new JobInstance();
				instance.setId(4321);
				instance.setDatafile(file.getAbsolutePath());
				instance.setFormat("marcxml");
				instance.setNamespace("http://wecat.atcult.it/");
				instance.setAliadaOntologyNamespace("http://aliada-project.eu/2014/aliada-ontology#");
				instance.setSparqlEndpointUrl("http://rdfstore:8080/openrdf-workbench");
				instance.setSparqlPassword(" ");
				instance.setSparqlUsername(" ");
				jobInstanceRepository.save(instance);
			}
			
			return newJob(4321);
		} catch (final Exception exception) {
			LOGGER.error(MessageCatalog._00051_IO_FAILURE, exception);
			return Response.serverError().build();	
		} finally {
			if (writer != null) {
				try { writer.close();} catch (Exception ex) {}
			}
		}
	}
	
	/**
	 * Creates a new job on the RDF-izer.
	 * 
	 * @param id the job identifier associated with this instance.
	 * @return a response which includes the URI of the new job.
	 */
	@PUT
	@Path("/jobs/{jobid}")
	public Response newJob(@PathParam("jobid") final Integer id) {
		if (!enabled) {
			return Response.status(Status.NOT_ACCEPTABLE).build();
		}
		
		LOGGER.debug(MessageCatalog._00029_NEW_JOB_REQUEST);
		
		if (id == null) {
			LOGGER.error(MessageCatalog._00028_MISSING_INPUT_PARAM, "jobid");
			return Response.status(Status.BAD_REQUEST).build();			
		}
		
		String path = null;
		JobResource newJobResource = null;
		try {
			final JobInstance configuration = cache.getJobInstance(id);
			if (configuration == null) {
				LOGGER.error(MessageCatalog._00032_JOB_INSTANCE_NOT_FOUND, id);
				return Response.status(Status.NOT_FOUND).build();								
			}
			
			final File datafile = new File(configuration.getDatafile());
			if (!datafile.canWrite()) {
				LOGGER.error(MessageCatalog._00020_WRONG_FILE_PERMISSIONS, datafile.getAbsolutePath());
				return Response.status(Status.BAD_REQUEST).build();						
			}
			
			path = datafile.getAbsolutePath();
			LOGGER.debug(MessageCatalog._00030_NEW_JOB_REQUEST_DEBUG, id, path);
			
			final String listenPath = listenPath(configuration.getFormat());
			if (listenPath == null) {
				LOGGER.error(MessageCatalog._00033_UNSUPPORTED_FORMAT, configuration.getFormat(), id);
				return Response.status(Status.BAD_REQUEST).build();										
			}
						
			final java.nio.file.Path source = Paths.get(path);
			final java.nio.file.Path target = Paths.get(listenPath + "/" + rdfizerDataFilename(datafile, id));
			newJobResource = new JobResource(configuration);
			newJobResource.setRunning(true);
			ManagementRegistrar.registerJob(newJobResource);
			jobRegistry.addJobResource(newJobResource);
						
			Files.move(source, target, REPLACE_EXISTING, ATOMIC_MOVE);

			configuration.setStartDate(new Timestamp(System.currentTimeMillis()));
			jobInstanceRepository.save(configuration);
			runningJobCount.incrementAndGet();
			
			return Response.created(uriInfo.getAbsolutePathBuilder().build()).build();
		} catch (JMException exception) {
			cleanUp(newJobResource, id);
			LOGGER.error(MessageCatalog._00045_MX_JOB_RESOURCE_REGISTRATION_FAILED, exception, id);
			return Response.serverError().build();	
		} catch (final IOException exception) {
			cleanUp(newJobResource, id);
			LOGGER.error(MessageCatalog._00051_IO_FAILURE, exception);
			return Response.serverError().build();						
		} catch (final DataAccessException exception)  {
			LOGGER.error(MessageCatalog._00031_DATA_ACCESS_FAILURE, exception);
			cleanUp(newJobResource, id);
			return Response.serverError().build();						
		} catch (final Exception exception)  {
			LOGGER.error(MessageCatalog._00034_NWS_SYSTEM_INTERNAL_FAILURE, exception);
			cleanUp(newJobResource, id);
			return Response.serverError().build();						
		}
	}
	
	/**
	 * Cleans up the runtime job definition within the system.
	 * 
	 * @param resource the {@link JobResource} (the job definition).
	 * @param id the job id. Although it is included in the preceeding instance, this is used because that could be null.
	 */
	private void cleanUp(final JobResource resource, final Integer id) {
		try {
			ManagementRegistrar.registerJob(resource);
		} catch (final JMException exception) {
			// Ignore
		}

		jobRegistry.removeJob(id);
	}
	
	/**
	 * Returns the path where RDF-izer is listening for input datafiles.
	 * 
	 * @param format the format associated with the current conversion request.
	 * @return the path where RDF-izer is listening for input datafiles.
	 */
	String listenPath(final String format) {
		if ("lido".equals(format)) {
			return lidoInputDir;
		} else if ("marcxml".equals(format)) {
			return marcXmlInputDir;
		} else if ("dc".equals(format)) {
			return dcXmlInputDir;
		} else if ("auth".equals(format)) {
			return authXmlInputDir;
		} else if ("unimarcxml".equals(format)) {
			return unimarcXmlInputDir;
		}
		return null;
	}
	
	/**
	 * Returns a valid RDFizer datafile name.
	 * RDFizer needs an input file with a given format, specifically composed by
	 * 
	 * name.suffix.jobid
	 * 
	 * where 
	 * 
	 * <ul>
	 * 	<li>name is the original input file name;</li>
	 * 	<li>suffix (optional) is the suffix of the original input file;</li>
	 * 	<li>jobid is the identifier that has been assigned to the job;</li>
	 * </ul>
	 * 
	 * @param file the input data file.
	 * @param jobId the job identifier.
	 * @return a valid RDFizer datafile name.
	 */
	String rdfizerDataFilename(final File file, final Integer jobId) {
		return new StringBuilder()
			.append(file.getName())
			.append(".")
			.append(jobId)
			.toString();
	}
	
	/**
	 * Initialises this resource.
	 */
	@PostConstruct
	public void init() {
		final MBeanServer mxServer = ManagementFactory.getPlatformMBeanServer();
		if (mxServer.isRegistered(ManagementRegistrar.RDFIZER_OBJECT_NAME)) {
			LOGGER.error(MessageCatalog._00043_MX_RESOURCE_ALREADY_REGISTERED, ManagementRegistrar.RDFIZER_OBJECT_NAME);
		} else {
			try {
				mxServer.registerMBean(this, ManagementRegistrar.RDFIZER_OBJECT_NAME);
				LOGGER.info(MessageCatalog._00044_MX_RESOURCE_REGISTERED, ManagementRegistrar.RDFIZER_OBJECT_NAME);				
			} catch (JMException exception) {
				LOGGER.error(MessageCatalog._00042_MX_SUBSYSTEM_FAILURE, exception);
			}
		}
	}
	
	/**
	 * Shutdown procedure for this resource.
	 */
	@PreDestroy
	public void destroy() {
		final MBeanServer mxServer = ManagementFactory.getPlatformMBeanServer();
		try {
			mxServer.unregisterMBean(ManagementRegistrar.RDFIZER_OBJECT_NAME);
		} catch (final Exception exception) {
			LOGGER.error(MessageCatalog._00042_MX_SUBSYSTEM_FAILURE, exception);
		}
	}

	@Override
	public int getRunningJobsCount() {
		return runningJobCount.get();
	}

	// TODO
	@Override
	public int getCompletedJobsCount() {
		return 0;
	}

	// TODO
	@Override
	public int getProcessedRecordsCount() {
		return 0;
	}
}