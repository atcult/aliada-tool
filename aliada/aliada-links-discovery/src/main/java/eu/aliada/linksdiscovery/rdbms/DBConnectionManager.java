// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-links-discovery
// Responsible: ALIADA Consortium
package eu.aliada.linksdiscovery.rdbms;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

import eu.aliada.linksdiscovery.log.MessageCatalog;
import eu.aliada.linksdiscovery.model.Job;
import eu.aliada.linksdiscovery.model.JobConfiguration;
import eu.aliada.linksdiscovery.model.Subjob;
import eu.aliada.linksdiscovery.model.SubjobConfiguration;
import eu.aliada.shared.log.Log;

/**
 * DB connection manager. 
 * It contains all the related functions with the DB: open, close connection,
 * execute SQL-s.
 *  
 * @author Idoia Murua
 * @since 1.0
 */
public class DBConnectionManager {
	/** For logging. */
	private static final Log LOGGER = new Log(DBConnectionManager.class);
	/* Job and subjob possible states */
	/** Subjob is idle */
	private final static String JOB_STATUS_IDLE = "idle"; 
	/** Subjob is running */
	private final static String JOB_STATUS_RUNNING = "running"; 
	/** Subjob has finished */
	private final static String JOB_STATUS_FINISHED = "finished"; 

	/** DB connection */
	private Connection conn;
	 
	/**
	 * Constructor. 
	 * Creates the DB connection.
	 *
	 * @since 1.0
	 */
	public DBConnectionManager() {
		getNewConnection();
	}

	/**
	 * Returns a new DB connection.
	 *
	 * @return	the new DB connection.
	 * @since 2.0
	 */
	public void getNewConnection() {
		InitialContext ic;
		DataSource ds;
		try {
			ic = new InitialContext();
			ds = (DataSource) ic.lookup("java:comp/env/jdbc/aliada");
			conn = ds.getConnection();
		} catch (NamingException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
		}
	}

	/**
	 * Closes the DB connection.
	 *
	 * @since 1.0
	 */
	public void closeConnection() {
		try {
			conn.close();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
		}
	}
	
	/**
	 * Returns the DB connection.
	 *
	 * @return	the DB connection.
	 * @since 1.0
	 */
	public Connection getConnection() {
		try {
			//Check first if the DB connection is still valid
			if(!this.conn.isValid(1)){
				LOGGER.debug(MessageCatalog._00026_GET_NEW_DB_CONNECTION);
				getNewConnection();
			}
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
		}
		return this.conn;
	}
 
	/**
	 * Gets the job configuration from the DB.
	 *
	 * @param jobId	the job identification.
	 * @return	the {@link eu.aliada.linksdiscovery.model.JobConfiguration}
	 * 			which contains the configuration of the job.
	 * @since 1.0
	 */
	public JobConfiguration getJobConfiguration(final Integer jobId) {
		JobConfiguration job = null;
		try {
			final Statement sta = getConnection().createStatement();
			final String sql = "SELECT * FROM linksdiscovery_job_instances WHERE job_id=" + jobId;
			final ResultSet resultSet = sta.executeQuery(sql);
			while (resultSet.next()) {
				job = new JobConfiguration();
				job.setId(jobId);
				job.setInputURI(resultSet.getString("input_uri"));
				job.setInputLogin(resultSet.getString("input_login"));
				job.setInputPassword(resultSet.getString("input_password"));
				job.setInputGraph(resultSet.getString("input_graph"));
				job.setOutputURI(resultSet.getString("output_uri"));
				job.setOutputLogin(resultSet.getString("output_login"));
				job.setOutputPassword(resultSet.getString("output_password"));
				job.setOutputGraph(resultSet.getString("output_graph"));
				job.setTmpDir(resultSet.getString("tmp_dir"));
				job.setClientAppBinDir(resultSet.getString("client_app_bin_dir"));
				job.setClientAppBinUser(resultSet.getString("client_app_user"));
		    }
			resultSet.close();
			sta.close();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return null;
		}
		return job;
	}
	
	/**
	 * Gets the subjob configuration from the DB.
	 *
	 * @param jobId	the job identification.
	 * @return	a list of {@link eu.aliada.linksdiscovery.model.SubjobConfiguration}
	 *			which contain the configuration of each subjob.
	 * @since 1.0
	 */
	public SubjobConfiguration[] getSubjobConfigurations(final Integer jobId) {
		final Vector<SubjobConfiguration> subJobsConfs = new Vector<SubjobConfiguration>();
		try {
			final Statement sta = getConnection().createStatement();
			final String sql = "SELECT * FROM linksdiscovery_subjob_instances WHERE job_id=" + jobId;
			final ResultSet resultSet = sta.executeQuery(sql);
			while (resultSet.next()) {
				final SubjobConfiguration subjobConf = new SubjobConfiguration();
				subjobConf.setId(resultSet.getInt("subjob_id"));
				subjobConf.setName(resultSet.getString("name"));
				subjobConf.setLinkingXMLConfigFilename(resultSet.getString("config_file"));
				subjobConf.setDs("ALIADA_ds");
				subjobConf.setLinkingNumThreads(resultSet.getInt("num_threads"));
				final int reloadSource = resultSet.getInt("reload_source");
				if(reloadSource == 1) {
					subjobConf.setLinkingReloadSource(true);
				} else {
					subjobConf.setLinkingReloadSource(false);
				}
				final int reloadTarget = resultSet.getInt("reload_target");
				if(reloadTarget == 1) {
					subjobConf.setLinkingReloadTarget(true);
				} else {
					subjobConf.setLinkingReloadTarget(false);
				}
				subJobsConfs.add(subjobConf);
		    }
			resultSet.close();
			sta.close();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return null;
		}
		return subJobsConfs.toArray(new SubjobConfiguration[]{});
	}
	
	/**
	 * Updates the start_date of the job.
	 *
	 * @param jobId	the job identification.
	 * @return true if the date has been updated correctly in the DB. False otherwise.
	 * @since 1.0
	 */
	public boolean updateJobStartDate(final int jobId){
		//Update start_date of job
    	try {
    		PreparedStatement preparedStatement = null;		
    		preparedStatement = getConnection().prepareStatement("UPDATE linksdiscovery_job_instances SET start_date = ? WHERE job_id = ?");
    		// (start_date, job_id)
    		// parameters start with 1
    		final java.util.Date today = new java.util.Date();
    		final java.sql.Timestamp todaySQL = new java.sql.Timestamp(today.getTime());
    		preparedStatement.setTimestamp(1, todaySQL);
    		preparedStatement.setInt(2, jobId);
    		preparedStatement.executeUpdate();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return false;
		}
  		return true;
	}

	/**
	 * Updates the end_date of the job.
	 *
	 * @param jobId	the job identification.
	 * @return true if the date has been updated correctly in the DB. False otherwise.
	 * @since 1.0
	 */
	public boolean updateJobEndDate(final int jobId){
		//Update end_date of job
    	try {
    		PreparedStatement preparedStatement = null;		
    		preparedStatement = getConnection().prepareStatement("UPDATE linksdiscovery_job_instances SET end_date = ? WHERE job_id = ?");
    		// (end_date, job_id)
    		// parameters start with 1
    		final java.util.Date today = new java.util.Date();
    		final java.sql.Timestamp todaySQL = new java.sql.Timestamp(today.getTime());
    		preparedStatement.setTimestamp(1, todaySQL);
    		preparedStatement.setInt(2, jobId);
    		preparedStatement.executeUpdate();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return false;
		}
    	return true;
	}

	/**
	 * Updates the XML configuration file of the SILK process, 
	 * for the specified subjob.
	 *
	 * @param jobId	the job identification.
	 * @param subjobId					the subjob identification.
	 * @param linkingXMLConfigFilename	Name of the XML configuration file for 
	 * 									the SILK process.
	 * @return true if the file has been updated correctly in the DB. False otherwise.
	 * @since 2.0
	 */
	public boolean updateSubjobConfigFile(final int jobId, final int subjobId, final String linkingXMLConfigFilename){
		//Update config_file of job
    	try {
    		PreparedStatement preparedStatement = null;		
    		preparedStatement = getConnection().prepareStatement("UPDATE linksdiscovery_subjob_instances SET config_file = ? WHERE job_id = ? AND subjob_id= ?");
    		// (config_file, job_id, subjob_id)
    		// parameters start with 1
    		preparedStatement.setString(1, linkingXMLConfigFilename);
    		preparedStatement.setInt(2, jobId);
    		preparedStatement.setInt(3, subjobId);
    		preparedStatement.executeUpdate();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return false;
		}
    	return true;
	}

	/**
	 * Inserts a new subjob in the DB.
	 *
	 * @param jobId						the job identification.
	 * @param subjobId					the subjob identification.
	 * @param subjobConf				{@link eu.aliada.linksdiscovery.model.SubjobConfiguration}
	 * 									which contain the configuration of the 
	 * 									subjob
	 * @param linkingXMLConfigFilename	Name of the XML configuration file for 
	 * 									the SILK process.
	 * @param tmpDir					the name of temporary folder.
	 * @return true if the subjob has been inserted correctly in the DB. 
	 *         False otherwise.
	 * @since 1.0
	 */
	public boolean insertSubjobToDB(final int jobId, final int subjobId, final SubjobConfiguration subjobConf, final String linkingXMLConfigFilename, final JobConfiguration jobConf){
    	try {
    		PreparedStatement preparedStatement = null;		
    		preparedStatement = getConnection().prepareStatement("INSERT INTO  linksdiscovery_subjob_instances VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, default, default, default)");
    		// (job_id, subjob_id, name, config_file, num_threads, reloadSource, reloadTarget, output_uri, 
    		//output_login, output_password, output_graph, tmp_dir, num_links, start_date, end_date)
    		// parameters start with 1
    		preparedStatement.setInt(1, jobId);
    		preparedStatement.setInt(2, subjobId);
    		preparedStatement.setString(3, subjobConf.getName());
    		preparedStatement.setString(4, linkingXMLConfigFilename);
    		preparedStatement.setInt(5, subjobConf.getLinkingNumThreads());
    		preparedStatement.setBoolean(6, subjobConf.isLinkingReloadSource());
    		preparedStatement.setBoolean(7, subjobConf.isLinkingReloadTarget());
    		preparedStatement.setString(8, jobConf.getOutputURI());
    		preparedStatement.setString(9, jobConf.getOutputLogin());
    		preparedStatement.setString(10, jobConf.getOutputPassword());
    		preparedStatement.setString(11, jobConf.getOutputGraph());
    		preparedStatement.setString(12, jobConf.getTmpDir());
    		preparedStatement.executeUpdate();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return false;
		}
    	return true;
	}
	
	/**
	 * Returns job and its corresponding subjobs information in the DB.
	 *
	 * @param jobId	the job identification.
	 * @return	the {@link eu.aliada.linksdiscovery.model.Job}
	 * 			which contains the job information.
	 * @since 1.0
	 */
	public Job getJob(final int jobId) {
		//Get the job information from the DB
		final Job job = new Job();
		job.setId(jobId);
		try {
			final Statement sta = getConnection().createStatement();
			String sql = "SELECT * FROM linksdiscovery_job_instances WHERE job_id=" + jobId;
			ResultSet resultSet = sta.executeQuery(sql);
			while (resultSet.next()) {
				job.setStartDate(resultSet.getTimestamp("start_date"));
				job.setEndDate(resultSet.getTimestamp("end_date"));
				//Determine job status
				String status = JOB_STATUS_IDLE;
				if(job.getStartDate() != null){
					status = JOB_STATUS_RUNNING;
					if(job.getEndDate() != null){
						status = JOB_STATUS_FINISHED;
					}
				}
				job.setStatus(status);
			}
			int totalNumLinks = 0;
			//Get subjobs of this job and their information in the DB.
			sql = "SELECT * FROM linksdiscovery_subjob_instances WHERE job_id=" + jobId;
			resultSet = sta.executeQuery(sql);
			while (resultSet.next()) {
				final Subjob subjob = new Subjob();
				subjob.setId(resultSet.getInt("subjob_id"));
				subjob.setName(resultSet.getString("name"));
				subjob.setStartDate(resultSet.getTimestamp("start_date"));
				subjob.setEndDate(resultSet.getTimestamp("end_date"));
				//Determine subjob status
				String status = JOB_STATUS_IDLE;
				if(subjob.getStartDate() != null){
					status = JOB_STATUS_RUNNING;
					if(subjob.getEndDate() != null){
						status = JOB_STATUS_FINISHED;
					}
				}
				subjob.setStatus(status);
				subjob.setNumLinks(resultSet.getInt("num_links"));
				//Add subjob to job structure
				job.addSubjob(subjob);
				//Compute total number of links generated by job
				totalNumLinks = totalNumLinks + subjob.getNumLinks();
			}
			//Set total number of links generated
			job.setNumLinks(totalNumLinks);
			resultSet.close();
			sta.close();
		} catch (SQLException exception) {
			LOGGER.error(MessageCatalog._00024_DATA_ACCESS_FAILURE, exception);
			return null;
		}
		return job;
	}
}
