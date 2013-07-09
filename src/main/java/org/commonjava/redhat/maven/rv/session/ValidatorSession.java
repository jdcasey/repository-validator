package org.commonjava.redhat.maven.rv.session;

import static org.commonjava.redhat.maven.rv.util.AnnotationUtils.findNamed;
import static org.commonjava.redhat.maven.rv.util.InputUtils.getFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ExtensionRelationship;
import org.apache.maven.graph.effective.rel.ParentRelationship;
import org.apache.maven.graph.effective.rel.PluginDependencyRelationship;
import org.apache.maven.graph.effective.rel.PluginRelationship;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.session.SimpleProjectToolsSession;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.commonjava.maven.atlas.spi.neo4j.effective.FileNeo4JEGraphDriver;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.comp.MavenComponentManager;
import org.commonjava.redhat.maven.rv.report.ValidationReport;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;

public class ValidatorSession
{

    private static final Set<String> CENTRAL_URL_ALIASES = new HashSet<String>()
    {
        {
            add( "http://repo.maven.apache.org/maven2" );
            add( "http://repo1.maven.org/maven2" );
        }

        private static final long serialVersionUID = 1L;
    };

    //    private final Logger logger = new Logger( getClass() );

    private final Set<String> pomExcludes;

    private final File repositoryDirectory;

    private final File workspaceDirectory;

    private final File reportsDirectory;

    private final File downloadsDirectory;

    private final Set<ProjectVersionRef> boms = new HashSet<ProjectVersionRef>();

    private final Map<ProjectVersionRef, Set<ModelProblem>> modelProblems =
        new HashMap<ProjectVersionRef, Set<ModelProblem>>();

    private final Map<ProjectVersionRef, Set<Exception>> errorsByRef = new HashMap<ProjectVersionRef, Set<Exception>>();

    private final Set<ProjectVersionRef> seen = new HashSet<ProjectVersionRef>();

    private final Set<ProjectVersionRef> missing = new HashSet<ProjectVersionRef>();

    private final Map<ProjectVersionRef, Set<String>> filesPerProject = new HashMap<ProjectVersionRef, Set<String>>();

    private ArtifactResolutionRequest baseArtifactResolutionRequest;

    private DefaultModelBuildingRequest baseModelBuildingRequest;

    private SimpleProjectToolsSession projectSession = new SimpleProjectToolsSession();

    private List<Exception> lowLevelErrors = new ArrayList<Exception>();

    private final EProjectWeb projectWeb;

    private LinkedList<ProjectVersionRef> projectsToResolve = new LinkedList<ProjectVersionRef>();

    private LinkedList<ArtifactRef> typesToResolve = new LinkedList<ArtifactRef>();

    private Map<ArtifactRef, List<String>> resolutionReposPerArtifact = new HashMap<ArtifactRef, List<String>>();

    private Set<ProjectRef> versionResolutionFailures = new HashSet<ProjectRef>();

    private String settingsXmlPath;

    private List<String> remoteRepoUrls;

    public static final class Builder
    {
        private final File repositoryDirectory;

        private final File workspaceDirectory;

        private File downloadsDirectory;

        private File reportsDirectory;

        private Set<String> pomExcludes = new HashSet<String>();

        private String settingsXml;

        private List<String> remoteRepos;

        private boolean graphRelationships;

        public Builder( final File repositoryDirectory, final File workspaceDirectory )
        {
            this.repositoryDirectory = repositoryDirectory;
            this.workspaceDirectory = workspaceDirectory;
        }

        public Builder withReportsDirectory( final File reportsDirectory )
        {
            this.reportsDirectory = reportsDirectory;
            return this;
        }

        public Builder withDownloadsDirectory( final File downloadsDirectory )
        {
            this.downloadsDirectory = downloadsDirectory;
            return this;
        }

        public Builder withPomExcludes( final String... excludes )
        {
            for ( final String exclude : excludes )
            {
                if ( exclude == null )
                {
                    continue;
                }

                final String[] parts = exclude.split( "\\s*,\\s*" );
                for ( String part : parts )
                {
                    part = part.trim();
                    if ( part.length() > 0 )
                    {
                        pomExcludes.add( part );
                    }
                }
            }

            return this;
        }

        public ValidatorSession build()
        {
            File reports = reportsDirectory;
            if ( reportsDirectory == null )
            {
                reports = new File( workspaceDirectory, "reports" );
            }

            File downloads = downloadsDirectory;
            if ( downloads == null )
            {
                downloads = new File( workspaceDirectory, "downloads" );
            }

            return new ValidatorSession( remoteRepos, settingsXml, repositoryDirectory, workspaceDirectory, reports,
                                         downloads, pomExcludes, graphRelationships );
        }

        public Builder withSettingsXmlPath( final String settingsXml )
        {
            this.settingsXml = settingsXml;
            return this;
        }

        public Builder withRemoteRepositoryUrls( final List<String> remoteRepos )
        {
            this.remoteRepos = remoteRepos;
            return this;
        }

        public Builder withGraphingEnabled( final boolean graphRelationships )
        {
            this.graphRelationships = graphRelationships;
            return this;
        }
    }

    private ValidatorSession( final List<String> remoteRepos, final String settingsXml, final File repositoryDirectory,
                              final File workspaceDirectory, final File reportsDirectory,
                              final File downloadsDirectory, final Set<String> pomExcludes,
                              final boolean graphRelationships )
    {
        this.remoteRepoUrls = remoteRepos;
        this.settingsXmlPath = settingsXml;
        this.repositoryDirectory = repositoryDirectory;
        this.workspaceDirectory = workspaceDirectory;
        this.reportsDirectory = reportsDirectory;
        this.downloadsDirectory = downloadsDirectory;

        final File depgraphDir = new File( workspaceDirectory, "depgraph" );
        depgraphDir.mkdirs();

        this.projectWeb =
            graphRelationships ? new EProjectWeb( new FileNeo4JEGraphDriver( depgraphDir, false ) ) : null;

        this.pomExcludes = Collections.unmodifiableSet( pomExcludes );
    }

    public List<String> getRemoteRepositoryUrls()
    {
        return remoteRepoUrls;
    }

    public String getSettingsXmlPath()
    {
        return settingsXmlPath;
    }

    public String[] getPomExcludes()
    {
        return pomExcludes.toArray( new String[] {} );
    }

    public void addBom( final ProjectVersionRef ref )
    {
        boms.add( ref );
    }

    public Set<ProjectVersionRef> getBoms()
    {
        return boms;
    }

    public boolean isMissing( final ProjectVersionRef id )
    {
        //        logger.info( "Has %s[toString=%s, hashCode=%s] been marked missing? %b", id.getClass()
        //                                                                                   .getName(), id, id.hashCode(),
        //                     missing.contains( id ) );
        //
        return missing.contains( id );
    }

    public boolean hasSeen( final ProjectVersionRef id )
    {
        //        logger.info( "Has %s[toString=%s, hashCode=%s] been marked seen? %b", id.getClass()
        //                                                                                .getName(), id, id.hashCode(),
        //                     seen.contains( id ) );
        //
        return seen.contains( id );
    }

    public void addSeen( final ProjectVersionRef id )
    {
        if ( id == null )
        {
            return;
        }

        //        logger.info( "Attempting to add seen project: %s", id );
        seen.add( id );
        //        logger.info( "Added? %s", added );
        //        logger.info( "Has %s[toString=%s, hashCode=%s] been marked seen? %b", id.getClass()
        //                                                                                .getName(), id, id.hashCode(),
        //                     seen.contains( id ) );
    }

    public void addMissing( final ProjectVersionRef id )
    {
        if ( id == null )
        {
            return;
        }

        //        logger.info( "Attempting to add missing project: %s", id );
        missing.add( id );
        //        logger.info( "Added? %s", added );
        //        logger.info( "Has %s[toString=%s, hashCode=%s] been marked missing? %b", id.getClass()
        //                                                                                   .getName(), id, id.hashCode(),
        //                     missing.contains( id ) );

        addSeen( id );
    }

    public void addModelProblem( final ProjectVersionRef ref, final ModelProblem problem )
    {
        //        logger.error( "PROBLEM in: %s was: %s", ref, problem );
        Set<ModelProblem> problems = modelProblems.get( ref );
        if ( problems == null )
        {
            problems = new HashSet<ModelProblem>();
            modelProblems.put( ref, problems );
        }

        problems.add( problem );
    }

    public void addError( final ProjectVersionRef src, final Exception error )
    {
        //        logger.error( "ERROR in: %s was: %s", src, error );
        Set<Exception> projectErrors = errorsByRef.get( src );
        if ( projectErrors == null )
        {
            projectErrors = new HashSet<Exception>();
            errorsByRef.put( src, projectErrors );
        }

        projectErrors.add( error );
    }

    public File getRepositoryDirectory()
    {
        return repositoryDirectory;
    }

    public DefaultModelBuildingRequest getBaseModelBuildingRequest()
    {
        return baseModelBuildingRequest;
    }

    public ArtifactResolutionRequest getBaseArtifactResolutionRequest()
    {
        return baseArtifactResolutionRequest;
    }

    public void initializeMavenComponents( final MavenComponentManager mavenManager )
        throws ValidationException
    {
        final List<Repository> repos = new ArrayList<Repository>();
        boolean hasCentral = false;
        if ( remoteRepoUrls != null )
        {
            for ( String url : remoteRepoUrls )
            {
                if ( url.endsWith( "/" ) )
                {
                    url = url.substring( 0, url.length() - 1 );
                }

                URL u;
                try
                {
                    u = new URL( url );
                }
                catch ( final MalformedURLException e )
                {
                    throw new ValidationException( "Invalid remote repository URL: '%s'. Reason: %s", e, url,
                                                   e.getMessage() );
                }

                final Repository repo = new Repository();
                repo.setUrl( url );

                repo.setId( u.toString() );
                if ( CENTRAL_URL_ALIASES.contains( url ) )
                {
                    repo.setId( "central" );
                    hasCentral = true;
                }

                repos.add( repo );
            }
        }

        final Repository repo = new Repository();
        if ( hasCentral )
        {
            repo.setId( "repo-validator-target" );
        }
        else
        {
            repo.setId( "central" );
        }

        try
        {
            repo.setUrl( repositoryDirectory.toURI()
                                            .toURL()
                                            .toExternalForm() );
        }
        catch ( final MalformedURLException e )
        {
            throw new ValidationException( "Failed to transform repository directory: %s into a URL: %s", e,
                                           repositoryDirectory, e.getMessage() );
        }

        // put this one up front, so we prefer it.
        repos.add( 0, repo );

        if ( settingsXmlPath != null )
        {
            final File settingsXml = getFile( settingsXmlPath, downloadsDirectory );
            try
            {
                mavenManager.configureFromSettings( projectSession, settingsXml );
            }
            catch ( final SettingsBuildingException e )
            {
                throw new ValidationException( "Failed to initialize Maven components for project-building: %s", e,
                                               e.getMessage() );
            }
            catch ( final MavenExecutionRequestPopulationException e )
            {
                throw new ValidationException( "Failed to initialize Maven components for project-building: %s", e,
                                               e.getMessage() );
            }
        }

        projectSession.setResolveRepositories( repos.toArray( new Repository[] {} ) );

        try
        {
            mavenManager.initializeSessionComponents( projectSession );
        }
        catch ( final ProjectToolsException e )
        {
            throw new ValidationException( "Failed to initialize Maven components for project-building: %s", e,
                                           e.getMessage() );
        }

        baseModelBuildingRequest = new DefaultModelBuildingRequest();
        baseModelBuildingRequest.setSystemProperties( System.getProperties() );
        baseModelBuildingRequest.setLocationTracking( true );
        //        baseModelBuildingRequest.setModelCache( new SimpleModelCache() );
        baseModelBuildingRequest.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0 );

        baseArtifactResolutionRequest = new ArtifactResolutionRequest();
        baseArtifactResolutionRequest.setRemoteRepositories( projectSession.getArtifactRepositoriesForResolution() );

        final File localRepo = new File( workspaceDirectory, "local-repo" );
        try
        {
            baseArtifactResolutionRequest.setLocalRepository( mavenManager.createLocalRepository( localRepo ) );
        }
        catch ( final InvalidRepositoryException e )
        {
            throw new ValidationException( "Failed to create local repository instance at: %s. Error: %s", e,
                                           localRepo, e.getMessage() );
        }
    }

    public RepositorySystemSession getRepositorySystemSession()
    {
        return projectSession.getRepositorySystemSession();
    }

    public List<RemoteRepository> getRemoteRepositories()
    {
        return projectSession.getRemoteRepositoriesForResolution();
    }

    public void addLowLevelError( final Exception error )
    {
        lowLevelErrors.add( error );
    }

    public void addParentLink( final ProjectVersionRef ref, final ProjectVersionRef parentRef )
    {
        projectWeb.add( new ParentRelationship( ref, parentRef ) );
    }

    public void addPluginLink( final ProjectVersionRef src, final ProjectVersionRef ref, final int index,
                               final boolean managed, final boolean reporting )
    {
        final PluginRelationship rel = new PluginRelationship( src, ref, index, managed, reporting );
        projectWeb.add( rel );
    }

    public void addExtensionLink( final ProjectVersionRef src, final ProjectVersionRef ref, final int index )
    {
        final ExtensionRelationship rel = new ExtensionRelationship( src, ref, index );
        projectWeb.add( rel );
    }

    public void addPluginDependencyLink( final ProjectVersionRef src, final ProjectVersionRef plugin,
                                         final ArtifactRef ref, final int index, final boolean managed )
    {
        final PluginDependencyRelationship rel = new PluginDependencyRelationship( src, plugin, ref, index, managed );
        projectWeb.add( rel );
    }

    public void addDependencyLink( final ProjectVersionRef src, final ArtifactRef ref, final DependencyScope scope,
                                   final int index, final boolean managed )
    {
        final DependencyRelationship rel = new DependencyRelationship( src, ref, scope, index, managed );
        projectWeb.add( rel );
    }

    public ProjectVersionRef getNextToProjectResolve()
    {
        final ProjectVersionRef ref = projectsToResolve.isEmpty() ? null : projectsToResolve.removeFirst();
        //        if ( ref != null )
        //        {
        //            logger.info( "[POM] -%s", ref );
        //        }

        return ref;
    }

    public ArtifactRef getNextArtifactToResolve()
    {
        final ArtifactRef ref = typesToResolve.isEmpty() ? null : typesToResolve.removeFirst();
        //        if ( ref != null )
        //        {
        //            logger.info( "[ARTIFACT] -%s", ref );
        //        }

        return ref;
    }

    public void addArtifactToResolve( ProjectVersionRef ref, final String type )
    {
        if ( ref instanceof ArtifactRef )
        {
            ref = ( (ArtifactRef) ref ).asProjectVersionRef();
        }

        if ( !hasSeen( ref ) && !projectsToResolve.contains( ref ) )
        {
            //            logger.info( "[POM] +%s", ref );
            projectsToResolve.addLast( ref );
        }

        final ArtifactRef artiRef =
            ( ref instanceof ArtifactRef ) ? (ArtifactRef) ref : new ArtifactRef( ref, type, null, false );
        if ( !typesToResolve.contains( artiRef ) )
        {
            //            logger.info( "[ARTIFACT] +%s", artiRef );
            typesToResolve.addLast( artiRef );
        }
    }

    public final File getWorkspaceDirectory()
    {
        return workspaceDirectory;
    }

    public final Map<ProjectVersionRef, Set<ModelProblem>> getModelProblems()
    {
        return modelProblems;
    }

    public final Map<ProjectVersionRef, Set<Exception>> getErrorsByRef()
    {
        return errorsByRef;
    }

    public final Set<ProjectVersionRef> getSeen()
    {
        return new HashSet<ProjectVersionRef>( seen );
    }

    public final Set<ProjectVersionRef> getMissing()
    {
        return new HashSet<ProjectVersionRef>( missing );
    }

    public final List<Exception> getLowLevelErrors()
    {
        return lowLevelErrors;
    }

    public EProjectWeb getProjectWeb()
    {
        return projectWeb;
    }

    public PrintWriter getReportWriter( final ValidationReport report )
        throws IOException
    {
        if ( !reportsDirectory.isDirectory() && !reportsDirectory.mkdirs() )
        {
            throw new IOException( "Failed to create reports directory!" );
        }

        final String named = findNamed( report.getClass() );
        if ( named == null )
        {
            throw new IOException( "Cannot find @Named annotation for: " + report.getClass()
                                                                                 .getName() );
        }

        final File reportFile = new File( reportsDirectory, named );
        return new PrintWriter( new FileWriter( reportFile ) );
    }

    public void addVersionResolutionFailure( final ProjectRef ref )
    {
        versionResolutionFailures.add( ref );
    }

    public Set<ProjectRef> getVersionResolutionFailures()
    {
        return new HashSet<ProjectRef>( versionResolutionFailures );
    }

    public void addProjectFiles( final ProjectVersionRef ref, final String[] files )
    {
        Set<String> projectFiles = this.filesPerProject.get( ref );
        if ( projectFiles == null )
        {
            projectFiles = new HashSet<String>();
            this.filesPerProject.put( ref, projectFiles );
        }

        projectFiles.addAll( Arrays.asList( files ) );
    }

    public Set<String> getProjectFiles( final ProjectVersionRef ref )
    {
        return filesPerProject.get( ref );
    }

    public Map<ProjectVersionRef, Set<String>> getAllProjectFiles()
    {
        return filesPerProject;
    }

    public void addArtifactResolutionRepositories( final ArtifactRef ref, final List<ArtifactRepository> repositories )
    {
        if ( repositories == null || repositories.isEmpty() )
        {
            return;
        }

        List<String> urls = resolutionReposPerArtifact.get( ref );
        if ( urls == null )
        {
            urls = new ArrayList<String>( repositories.size() );
            resolutionReposPerArtifact.put( ref, urls );
        }

        for ( final ArtifactRepository artifactRepository : repositories )
        {
            urls.add( artifactRepository.getUrl() );
        }
    }

    public List<String> getArtifactResolutionRepositories( final ArtifactRef ref )
    {
        return resolutionReposPerArtifact.get( ref );
    }

    public Map<ArtifactRef, List<String>> getAllArtifactResolutionRepositories()
    {
        return resolutionReposPerArtifact;
    }

    public List<RemoteRepository> getRemoteRepositoriesForResolution()
    {
        return projectSession.getRemoteRepositoriesForResolution();
    }

}
