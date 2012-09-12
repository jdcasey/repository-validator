package org.commonjava.redhat.maven.rv.session;

import static org.commonjava.redhat.maven.rv.util.AnnotationUtils.findNamed;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ExtensionRelationship;
import org.apache.maven.graph.effective.rel.PluginDependencyRelationship;
import org.apache.maven.graph.effective.rel.PluginRelationship;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.mae.project.session.SimpleProjectToolsSession;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.repository.RepositorySystem;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.comp.DirModelResolver;
import org.commonjava.redhat.maven.rv.report.ValidationReport;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;

public class ValidatorSession
{

    //    private final Logger logger = new Logger( getClass() );

    private final Set<String> pomExcludes;

    private final File repositoryDirectory;

    private final File workspaceDirectory;

    private final Map<ProjectVersionRef, Set<ModelProblem>> modelProblems =
        new HashMap<ProjectVersionRef, Set<ModelProblem>>();

    private final Map<ProjectVersionRef, Set<Exception>> modelErrors = new HashMap<ProjectVersionRef, Set<Exception>>();

    private final Set<ProjectVersionRef> seen = new HashSet<ProjectVersionRef>();

    private final Set<ProjectVersionRef> missing = new HashSet<ProjectVersionRef>();

    private DirModelResolver modelResolver;

    private ArtifactResolutionRequest baseArtifactResolutionRequest;

    private DefaultModelBuildingRequest baseModelBuildingRequest;

    private SimpleProjectToolsSession projectSession = new SimpleProjectToolsSession();

    private List<Exception> lowLevelErrors = new ArrayList<Exception>();

    private EProjectWeb projectWeb = new EProjectWeb();

    private LinkedList<ProjectVersionRef> projectsToResolve = new LinkedList<ProjectVersionRef>();

    private LinkedList<ArtifactRef> typesToResolve = new LinkedList<ArtifactRef>();

    public static final class Builder
    {
        private final File repositoryDirectory;

        private final File workspaceDirectory;

        private Set<String> pomExcludes = new HashSet<String>();

        public Builder( final File repositoryDirectory, final File workspaceDirectory )
        {
            this.repositoryDirectory = repositoryDirectory;
            this.workspaceDirectory = workspaceDirectory;
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
            return new ValidatorSession( repositoryDirectory, workspaceDirectory, pomExcludes );
        }
    }

    private ValidatorSession( final File repositoryDirectory, final File workspaceDirectory,
                              final Set<String> pomExcludes )
    {
        this.repositoryDirectory = repositoryDirectory;
        this.workspaceDirectory = workspaceDirectory;
        this.pomExcludes = Collections.unmodifiableSet( pomExcludes );
    }

    public String[] getPomExcludes()
    {
        return pomExcludes.toArray( new String[] {} );
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
        seen.add( id );
        //        logger.info( "Has %s[toString=%s, hashCode=%s] been marked seen? %b", id.getClass()
        //                                                                                .getName(), id, id.hashCode(),
        //                     seen.contains( id ) );
    }

    public void addMissing( final ProjectVersionRef id )
    {
        missing.add( id );
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

    public void addModelError( final ProjectVersionRef src, final Exception error )
    {
        //        logger.error( "ERROR in: %s was: %s", src, error );
        Set<Exception> errors = modelErrors.get( src );
        if ( errors == null )
        {
            errors = new HashSet<Exception>();
            modelErrors.put( src, errors );
        }

        errors.add( error );
    }

    public File getRepositoryDirectory()
    {
        return repositoryDirectory;
    }

    public ModelResolver getModelResolver()
    {
        if ( modelResolver == null )
        {
            modelResolver = new DirModelResolver( getRepositoryDirectory() );
        }

        return modelResolver;
    }

    public DefaultModelBuildingRequest getBaseModelBuildingRequest()
    {
        return baseModelBuildingRequest;
    }

    public ArtifactResolutionRequest getBaseArtifactResolutionRequest()
    {
        return baseArtifactResolutionRequest;
    }

    public void initializeMavenComponents( final SessionInitializer sessionInitializer,
                                           final RepositorySystem repoSystem )
        throws ValidationException
    {
        final Repository repo = new Repository();
        repo.setId( "validation-target" );
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

        projectSession.setResolveRepositories( repo );

        try
        {
            sessionInitializer.initializeSessionComponents( projectSession );
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

        final File localRepo = new File( workspaceDirectory, "local-repo" );
        try
        {
            baseArtifactResolutionRequest.setLocalRepository( repoSystem.createLocalRepository( localRepo ) );
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

    public final Map<ProjectVersionRef, Set<Exception>> getModelErrors()
    {
        return modelErrors;
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
        final File reportsDir = new File( workspaceDirectory, "reports" );
        if ( !reportsDir.isDirectory() && !reportsDir.mkdirs() )
        {
            throw new IOException( "Failed to create reports directory!" );
        }

        final String named = findNamed( report.getClass() );
        if ( named == null )
        {
            throw new IOException( "Cannot find @Named annotation for: " + report.getClass()
                                                                                 .getName() );
        }

        final File reportFile = new File( reportsDir, named );
        return new PrintWriter( new FileWriter( reportFile ) );
    }

}
