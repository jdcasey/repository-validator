package org.commonjava.redhat.maven.rv.session;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.mae.project.session.SimpleProjectToolsSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.repository.RepositorySystem;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.comp.DirModelResolver;
import org.commonjava.redhat.maven.rv.comp.SimpleModelCache;

public class ValidatorSession
{

    private final Set<String> pomExcludes;

    private final File repositoryDirectory;

    private final File workspaceDirectory;

    private final Map<String, Set<ModelProblem>> modelProblems = new HashMap<String, Set<ModelProblem>>();

    private final Map<String, Set<Exception>> modelErrors = new HashMap<String, Set<Exception>>();

    private final Set<String> seen = new HashSet<String>();

    private DirModelResolver modelResolver;

    private ArtifactResolutionRequest baseArtifactResolutionRequest;

    private DefaultModelBuildingRequest baseModelBuildingRequest;

    private SimpleProjectToolsSession projectSession = new SimpleProjectToolsSession();

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
            pomExcludes.addAll( Arrays.asList( excludes ) );
            return this;
        }

        public ValidatorSession build()
        {
            return new ValidatorSession( repositoryDirectory, workspaceDirectory, pomExcludes );
        }
    }

    public boolean hasSeenModel( final String id )
    {
        return seen.contains( id );
    }

    public void addSeenModel( final Model model )
    {
        seen.add( model.getId() );
    }

    public void addModelProblem( final String pom, final ModelProblem problem )
    {
        Set<ModelProblem> problems = modelProblems.get( pom );
        if ( problems == null )
        {
            problems = new HashSet<ModelProblem>();
            modelProblems.put( pom, problems );
        }

        problems.add( problem );
    }

    public void addModelError( final String pom, final Exception error )
    {
        Set<Exception> errors = modelErrors.get( pom );
        if ( errors == null )
        {
            errors = new HashSet<Exception>();
            modelErrors.put( pom, errors );
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
        baseModelBuildingRequest.setLocationTracking( true );
        baseModelBuildingRequest.setModelCache( new SimpleModelCache() );
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

}
