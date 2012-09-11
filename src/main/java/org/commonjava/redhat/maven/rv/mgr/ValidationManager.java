package org.commonjava.redhat.maven.rv.mgr;

import static org.commonjava.redhat.maven.rv.model.ArtifactReferenceUtils.toArtifactRef;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.common.version.InvalidVersionSpecificationException;
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
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.plugin.version.PluginVersionResult;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.redhat.maven.rv.ValidationException;
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
                validateProjectGraph( model, session );
            }
        }

        // TODO: Report errors encountered and logged in session!
    }

    private ModelSource resolveModel( final ProjectVersionRef ref, final ValidatorSession session,
                                      final ProjectVersionRef src )
    {
        ModelSource source = null;
        try
        {
            // TODO: What about version ranges??
            source = session.getModelResolver()
                            .resolveModel( ref.getGroupId(), ref.getArtifactId(), ref.getVersionSpec()
                                                                                     .renderStandard() );
        }
        catch ( final UnresolvableModelException e )
        {
            logger.info( "Failed to resolve: %s, Error was: %s", e, ref, e.getMessage() );
            session.addModelError( src, e );

            logger.info( "Marking missing: %s[%s]", ref.getClass()
                                                       .getName(), ref );
            session.addMissing( ref );
        }

        return source;
    }

    private Model buildModel( final File pomFile, final ValidatorSession session )
    {
        final ModelSource source = new FileModelSource( pomFile );
        final Model model = buildModel( source, session, source.getLocation() );

        if ( model != null )
        {
            session.addSeen( toArtifactRef( model, session ) );
        }

        return model;
    }

    private Model buildModel( final ModelSource source, final ValidatorSession session, final String pomLocation )
    {
        final DefaultModelBuildingRequest mbr =
            new DefaultModelBuildingRequest( session.getBaseModelBuildingRequest() ).setModelSource( source )
                                                                                    .setModelResolver( session.getModelResolver() );

        Model model = null;
        ProjectVersionRef ref = null;
        try
        {
            final ModelBuildingResult result = modelBuilder.build( mbr );
            model = result.getEffectiveModel();

            if ( model == null )
            {
                final Model mdl = readRawModel( source, session );
                if ( mdl != null )
                {
                    ref = toArtifactRef( mdl, session );
                }
            }
            else
            {
                ref = toArtifactRef( model, session );
            }

            if ( ref != null )
            {
                final List<ModelProblem> problems = result.getProblems();
                if ( problems != null )
                {
                    for ( final ModelProblem problem : problems )
                    {
                        session.addModelProblem( ref, problem );
                    }
                }
            }
        }
        catch ( final ModelBuildingException e )
        {
            final Model mdl = readRawModel( source, session );
            if ( mdl != null )
            {
                ref = toArtifactRef( mdl, session );
            }

            if ( ref != null )
            {
                final List<ModelProblem> problems = e.getProblems();
                if ( problems != null )
                {
                    for ( final ModelProblem problem : problems )
                    {
                        session.addModelProblem( ref, problem );
                    }
                }
            }

            session.addLowLevelError( new ValidationException( "Failed to build Model for POM: %s. Reason: %s", e,
                                                               pomLocation, e.getMessage() ) );
        }

        return model;
    }

    private Model readRawModel( final ModelSource source, final ValidatorSession session )
    {
        try
        {
            return new MavenXpp3Reader().read( source.getInputStream() );
        }
        catch ( final IOException e )
        {
            session.addLowLevelError( new ValidationException( "Failed to read raw model: %s. Reason: %s", e,
                                                               source.getLocation(), e.getMessage() ) );
        }
        catch ( final XmlPullParserException e )
        {
            session.addLowLevelError( new ValidationException( "Failed to parse raw model: %s. Reason: %s", e,
                                                               source.getLocation(), e.getMessage() ) );
        }

        return null;
    }

    private void resolveArtifact( final ArtifactRef ref, final ValidatorSession session, final ProjectVersionRef src )
    {
        final ArtifactResolutionRequest req =
            new ArtifactResolutionRequest( session.getBaseArtifactResolutionRequest() );

        req.setArtifact( repoSystem.createArtifact( ref.getGroupId(), ref.getArtifactId(), ref.getVersionSpec()
                                                                                              .renderStandard(),
                                                    ref.getType() ) );

        final ArtifactResolutionResult result = repoSystem.resolve( req );

        final List<Exception> exceptions = result.getExceptions();
        if ( exceptions != null )
        {
            session.addMissing( ref );
            for ( final Exception exception : exceptions )
            {
                session.addModelError( src, exception );
            }
        }
        else
        {
            session.addSeen( ref );
        }
    }

    private void validateProjectGraph( final Model model, final ValidatorSession session )
    {
        logger.info( "Validating project references for: %s", model );

        final ProjectVersionRef src = toArtifactRef( model, session );
        validateDependencySections( model, session, src );
        validateBuild( model.getBuild(), session, src );
        validateReporting( model, session, src );

        final List<Profile> profiles = model.getProfiles();
        if ( profiles != null )
        {
            for ( final Profile profile : profiles )
            {
                validateDependencySections( profile, session, src );
                validateBuild( profile.getBuild(), session, src );
                validateReporting( profile, session, src );
            }
        }
    }

    private void validateDependencySections( final ModelBase model, final ValidatorSession session,
                                             final ProjectVersionRef src )
    {
        final DependencyManagement dm = model.getDependencyManagement();
        if ( dm != null )
        {
            final List<Dependency> deps = dm.getDependencies();
            if ( deps != null )
            {
                validateDependencies( deps, session, true, src );
            }
        }

        final List<Dependency> deps = model.getDependencies();
        if ( deps != null )
        {
            validateDependencies( deps, session, false, src );
        }
    }

    private void validateBuild( final BuildBase build, final ValidatorSession session, final ProjectVersionRef src )
    {
        if ( build != null )
        {
            if ( build instanceof Build )
            {
                final Build b = (Build) build;
                final List<Extension> extensions = b.getExtensions();
                if ( extensions != null )
                {
                    validateExtensions( extensions, session, src );
                }
            }

            final PluginManagement pm = build.getPluginManagement();
            if ( pm != null )
            {
                final List<Plugin> plugins = pm.getPlugins();
                if ( plugins != null )
                {
                    validatePlugins( plugins, session, true, src );
                }
            }

            final List<Plugin> plugins = build.getPlugins();
            if ( plugins != null )
            {
                validatePlugins( plugins, session, false, src );
            }
        }
    }

    private void validateReporting( final ModelBase model, final ValidatorSession session, final ProjectVersionRef src )
    {
        logger.info( "Validating reporting: %s", src );
        final Reporting reporting = model.getReporting();
        if ( reporting != null )
        {
            final List<ReportPlugin> plugins = reporting.getPlugins();
            if ( plugins != null )
            {
                int idx = 0;
                for ( final ReportPlugin plugin : plugins )
                {
                    ProjectRef ref = toArtifactRef( plugin, session );
                    if ( !( ref instanceof ProjectVersionRef ) )
                    {
                        logger.info( "Resolving version for: %s", ref );
                        ref = resolvePluginVersion( ref, session, src );
                    }

                    if ( ref != null )
                    {
                        session.addPluginLink( src, (ProjectVersionRef) ref, idx, false, true );
                        validateModelAndArtifact( (ProjectVersionRef) ref, "maven-plugin", session, src );
                    }

                    idx++;
                }
            }
        }
    }

    private void validatePlugins( final List<Plugin> plugins, final ValidatorSession session, final boolean managed,
                                  final ProjectVersionRef src )
    {
        logger.info( "Validating plugins: %s", src );
        if ( plugins != null )
        {
            int idx = 0;
            for ( final Plugin plugin : plugins )
            {
                ProjectRef ref = toArtifactRef( plugin, session );
                if ( !( ref instanceof ProjectVersionRef ) )
                {
                    logger.info( "Resolving version for: %s", ref );
                    ref = resolvePluginVersion( ref, session, src );
                }

                session.addPluginLink( src, (ProjectVersionRef) ref, idx, managed, false );
                validateModelAndArtifact( (ProjectVersionRef) ref, "maven-plugin", session, src );

                idx++;
            }
        }
    }

    private void validateExtensions( final List<Extension> extensions, final ValidatorSession session,
                                     final ProjectVersionRef src )
    {
        logger.info( "Validating extensions: %s", src );
        if ( extensions != null )
        {
            int idx = 0;
            for ( final Extension extension : extensions )
            {
                final ProjectVersionRef ref = toArtifactRef( extension, session );
                session.addExtensionLink( src, ref, idx );
                validateModelAndArtifact( ref, "jar", session, src );

                idx++;
            }
        }
    }

    private void validateDependencies( final List<Dependency> deps, final ValidatorSession session,
                                       final boolean managed, final ProjectVersionRef src )
    {
        logger.info( "Validating dependencies: %s", src );
        if ( deps != null )
        {
            int idx = 0;
            for ( final Dependency dependency : deps )
            {
                final ArtifactRef ref = toArtifactRef( dependency, session );
                session.addDependencyLink( src, ref, DependencyScope.getScope( dependency.getScope() ), idx, managed );
                validateModelAndArtifact( ref, dependency.getType(), session, src );

                idx++;
            }
        }
    }

    private void validateModelAndArtifact( final ProjectVersionRef base, final String type,
                                           final ValidatorSession session, final ProjectVersionRef src )
    {
        ProjectVersionRef pom = base;
        if ( pom instanceof ArtifactRef )
        {
            pom = ( (ArtifactRef) pom ).asProjectVersionRef();
        }

        logger.info( "Checking missing: %s[%s]", pom.getClass()
                                                    .getName(), pom );
        if ( session.isMissing( pom ) )
        {
            return;
        }

        final ArtifactRef artifact = toArtifactRef( pom, type, session );

        logger.info( "Validating model: %s with artifact of type: %s (referenced from: %s)", pom, type, src );

        if ( !session.hasSeen( pom ) )
        {
            logger.info( "Building model for: %s", pom );

            // build the model
            final ModelSource source = resolveModel( pom, session, src );
            if ( source != null )
            {
                logger.info( "Resolved to: %s", source.getLocation() );
                final Model model = buildModel( source, session, source.getLocation() );

                if ( model != null )
                {
                    session.addSeen( pom );

                    // validate the project graph for the plugin
                    validateProjectGraph( model, session );
                }
                else
                {
                    logger.info( "Marking missing: %s[%s]", pom.getClass()
                                                               .getName(), pom );
                    session.addMissing( pom );
                }
            }
        }

        if ( !session.hasSeen( artifact ) )
        {
            logger.info( "Resolving: %s:%s", artifact, type );

            // resolve the jar (maven-plugin type)
            resolveArtifact( artifact, session, src );
        }
    }

    private ProjectVersionRef resolvePluginVersion( final ProjectRef ref, final ValidatorSession session,
                                                    final ProjectVersionRef src )
    {
        final Plugin plugin = new Plugin();
        plugin.setGroupId( ref.getGroupId() );
        plugin.setArtifactId( ref.getArtifactId() );

        final DefaultPluginVersionRequest req =
            new DefaultPluginVersionRequest( plugin, session.getRepositorySystemSession(),
                                             session.getRemoteRepositories() );

        String version = null;
        try
        {
            final PluginVersionResult result = pluginVersionResolver.resolve( req );

            version = result.getVersion();

            return new ProjectVersionRef( ref.getGroupId(), ref.getArtifactId(), version );
        }
        catch ( final PluginVersionResolutionException e )
        {
            session.addModelError( src, e );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addLowLevelError( new ValidationException(
                                                               "Failed to parse version: '%s'\nPlugin: %s\nPOM: %s\nReason: %s",
                                                               e, version, ref, src, e.getMessage() ) );
        }

        return null;
    }

}
