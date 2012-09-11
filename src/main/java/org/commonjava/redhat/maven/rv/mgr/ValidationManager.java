package org.commonjava.redhat.maven.rv.mgr;

import java.io.File;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.plugin.version.PluginVersionResult;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.DirectoryScanner;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.model.ArtifactRef;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.util.logging.Logger;

@Singleton
public class ValidationManager
{
    private static final String[] POM_INCLUDES = { "**/*.pom", "**/pom.xml" };

    private final Logger logger = new Logger( getClass() );

    @Inject
    private ModelBuilder modelBuilder;

    @Inject
    private PluginVersionResolver pluginVersionResolver;

    @Inject
    private RepositorySystem repoSystem;

    @Inject
    private SessionInitializer sessionInitializer;

    public void validate( final ValidatorSession session )
        throws ValidationException
    {
        session.initializeMavenComponents( sessionInitializer, repoSystem );

        final File repositoryDir = session.getRepositoryDirectory();

        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( repositoryDir );
        scanner.setIncludes( POM_INCLUDES );
        scanner.setFollowSymlinks( true );
        scanner.setExcludes( session.getPomExcludes() );
        scanner.addDefaultExcludes();

        scanner.scan();

        final String[] poms = scanner.getIncludedFiles();

        for ( final String pom : poms )
        {
            final File pomFile = new File( repositoryDir, pom );

            final Model model = buildModel( pomFile, session );

            if ( model != null )
            {
                logger.info( "Validating: %s", pom );
                validateProjectGraph( model, session, pom );
            }
        }

        // TODO: Report errors encountered and logged in session!
    }

    private ModelSource resolveModel( final ArtifactRef ref, final ValidatorSession session, final String pomLocation )
    {
        ModelSource source = null;
        try
        {
            source = session.getModelResolver()
                            .resolveModel( ref.getGroupId(), ref.getArtifactId(), ref.getVersion() );
        }
        catch ( final UnresolvableModelException e )
        {
            logger.info( "Failed to resolve: %s, Error was: %s", e, ref, e.getMessage() );
            session.addModelError( pomLocation, e );
        }

        return source;
    }

    private Model buildModel( final File pomFile, final ValidatorSession session )
    {
        final ModelSource source = new FileModelSource( pomFile );
        final Model model = buildModel( source, session, source.getLocation() );

        if ( model != null )
        {
            session.addSeen( new ArtifactRef( model ) );
        }

        return model;
    }

    private Model buildModel( final ModelSource source, final ValidatorSession session, final String pomLocation )
    {
        final DefaultModelBuildingRequest mbr =
            new DefaultModelBuildingRequest( session.getBaseModelBuildingRequest() ).setModelSource( source )
                                                                                    .setModelResolver( session.getModelResolver() );

        Model model = null;
        try
        {
            final ModelBuildingResult result = modelBuilder.build( mbr );

            final List<ModelProblem> problems = result.getProblems();
            if ( problems != null )
            {
                for ( final ModelProblem problem : problems )
                {
                    logger.info( "Building: %s, PROBLEM: %s", pomLocation, problem );
                    session.addModelProblem( pomLocation, problem );
                }
            }

            model = result.getEffectiveModel();
        }
        catch ( final ModelBuildingException e )
        {
            final List<ModelProblem> problems = e.getProblems();
            if ( problems != null )
            {
                for ( final ModelProblem problem : problems )
                {
                    logger.info( "ERROR Building: %s, PROBLEM: %s", pomLocation, problem );
                    session.addModelProblem( pomLocation, problem );
                }
            }

            logger.info( "ERROR Building: %s, ERROR: %s", e, pomLocation, e.getMessage() );
            session.addModelError( pomLocation, e );
        }

        return model;
    }

    private void resolveArtifact( final ArtifactRef ref, final ValidatorSession session, final String pomLocation )
    {
        final ArtifactResolutionRequest req =
            new ArtifactResolutionRequest( session.getBaseArtifactResolutionRequest() );

        req.setArtifact( repoSystem.createArtifact( ref.getGroupId(), ref.getArtifactId(), ref.getVersion(),
                                                    ref.getType() ) );

        final ArtifactResolutionResult result = repoSystem.resolve( req );

        final List<Exception> exceptions = result.getExceptions();
        if ( exceptions != null )
        {
            session.addMissing( ref );
            for ( final Exception exception : exceptions )
            {
                session.addModelError( pomLocation, exception );
            }
        }
        else
        {
            session.addSeen( ref );
        }
    }

    //    private String relativize( final File pomFile, final File repositoryDirectory )
    //    {
    //        final String repoPath = repositoryDirectory.getAbsolutePath();
    //        final String absPath = pomFile.getAbsolutePath();
    //
    //        final String relativePath = absPath.substring( 0, repoPath.length() );
    //        return relativePath;
    //    }

    private void validateProjectGraph( final Model model, final ValidatorSession session, String pomLocation )
    {
        logger.info( "Validating project references for: %s", pomLocation );

        validateDependencySections( model, session, pomLocation );
        validateBuild( model.getBuild(), session, pomLocation );
        validateReporting( model, session, pomLocation );

        final List<Profile> profiles = model.getProfiles();
        if ( profiles != null )
        {
            for ( final Profile profile : profiles )
            {
                final String location = pomLocation = "#profile[" + profile.getId() + "]";
                validateDependencySections( profile, session, location );
                validateBuild( profile.getBuild(), session, location );
            }
        }
    }

    private void validateDependencySections( final ModelBase model, final ValidatorSession session,
                                             final String pomLocation )
    {
        final DependencyManagement dm = model.getDependencyManagement();
        if ( dm != null )
        {
            final List<Dependency> deps = dm.getDependencies();
            if ( deps != null )
            {
                validateDependencies( deps, session, pomLocation + "#managedDependencies" );
            }
        }

        final List<Dependency> deps = model.getDependencies();
        if ( deps != null )
        {
            validateDependencies( deps, session, pomLocation + "#dependencies" );
        }
    }

    private void validateBuild( final BuildBase build, final ValidatorSession session, final String pomLocation )
    {
        if ( build != null )
        {
            if ( build instanceof Build )
            {
                final Build b = (Build) build;
                final List<Extension> extensions = b.getExtensions();
                if ( extensions != null )
                {
                    validateExtensions( extensions, session, pomLocation + "#extensions" );
                }
            }

            final PluginManagement pm = build.getPluginManagement();
            if ( pm != null )
            {
                final List<Plugin> plugins = pm.getPlugins();
                if ( plugins != null )
                {
                    validatePlugins( plugins, session, pomLocation + "#managedPlugins" );
                }
            }

            final List<Plugin> plugins = build.getPlugins();
            if ( plugins != null )
            {
                validatePlugins( plugins, session, pomLocation + "#plugins" );
            }
        }
    }

    private void validateReporting( final ModelBase model, final ValidatorSession session, final String pomLocation )
    {
        logger.info( "Validating reporting: %s", pomLocation );
        final Reporting reporting = model.getReporting();
        if ( reporting != null )
        {
            final List<ReportPlugin> plugins = reporting.getPlugins();
            if ( plugins != null )
            {
                for ( final ReportPlugin plugin : plugins )
                {
                    ArtifactRef ref = new ArtifactRef( plugin );
                    if ( !ref.isComplete() )
                    {
                        logger.info( "Resolving version for: %s", ref );
                        ref = resolvePluginVersion( ref, session, pomLocation );
                    }

                    validateModelAndArtifact( ref, "maven-plugin", session, pomLocation );
                }
            }
        }
    }

    private void validatePlugins( final List<Plugin> plugins, final ValidatorSession session, final String pomLocation )
    {
        logger.info( "Validating plugins: %s", pomLocation );
        if ( plugins != null )
        {
            for ( final Plugin plugin : plugins )
            {
                ArtifactRef ref = new ArtifactRef( plugin );
                if ( !ref.isComplete() )
                {
                    logger.info( "Resolving version for: %s", ref );
                    ref = resolvePluginVersion( ref, session, pomLocation );
                }

                validateModelAndArtifact( ref, "maven-plugin", session, pomLocation );
            }
        }
    }

    private void validateExtensions( final List<Extension> extensions, final ValidatorSession session,
                                     final String pomLocation )
    {
        logger.info( "Validating extensions: %s", pomLocation );
        if ( extensions != null )
        {
            for ( final Extension extension : extensions )
            {
                final ArtifactRef ref = new ArtifactRef( extension );
                validateModelAndArtifact( ref, "jar", session, pomLocation );
            }
        }
    }

    private void validateDependencies( final List<Dependency> deps, final ValidatorSession session,
                                       final String pomLocation )
    {
        logger.info( "Validating dependencies: %s", pomLocation );
        if ( deps != null )
        {
            for ( final Dependency dependency : deps )
            {
                final ArtifactRef ref = new ArtifactRef( dependency );
                validateModelAndArtifact( ref, dependency.getType(), session, pomLocation );
            }
        }
    }

    private void validateModelAndArtifact( final ArtifactRef ref, final String type, final ValidatorSession session,
                                           final String pomLocation )
    {
        if ( session.isMissing( ref ) )
        {
            return;
        }

        if ( !ref.isComplete() )
        {
            logger.error( "NO VERSION for: %s. Skipping validation.", ref );
            return;
        }

        final ArtifactRef pom = new ArtifactRef( ref, "pom" );
        final ArtifactRef artifact = new ArtifactRef( ref, type );

        logger.info( "Validating model: %s with artifact of type: %s (referenced from: %s)", ref, type, pomLocation );

        if ( !session.hasSeen( pom ) )
        {
            logger.info( "Building model for: %s", pom );

            // build the model
            final ModelSource source = resolveModel( pom, session, pomLocation );
            if ( source != null )
            {
                logger.info( "Resolved to: %s", source.getLocation() );
                final Model model = buildModel( source, session, source.getLocation() );

                if ( model != null )
                {
                    session.addSeen( pom );

                    // validate the project graph for the plugin
                    validateProjectGraph( model, session, source.getLocation() );
                }
                else
                {
                    session.addMissing( pom );
                }
            }
        }

        if ( !session.hasSeen( artifact ) )
        {
            logger.info( "Resolving: %s:%s", artifact, type );

            // resolve the jar (maven-plugin type)
            resolveArtifact( artifact, session, pomLocation );
        }
    }

    private ArtifactRef resolvePluginVersion( final ArtifactRef ref, final ValidatorSession session,
                                              final String pomLocation )
    {
        final Plugin plugin = new Plugin();
        plugin.setGroupId( ref.getGroupId() );
        plugin.setArtifactId( ref.getArtifactId() );

        final DefaultPluginVersionRequest req =
            new DefaultPluginVersionRequest( plugin, session.getRepositorySystemSession(),
                                             session.getRemoteRepositories() );

        try
        {
            final PluginVersionResult result = pluginVersionResolver.resolve( req );

            final String version = result.getVersion();
            ref.setVersion( version );
        }
        catch ( final PluginVersionResolutionException e )
        {
            session.addModelError( pomLocation + "[" + ref + "]", e );
        }

        return ref;
    }

}
