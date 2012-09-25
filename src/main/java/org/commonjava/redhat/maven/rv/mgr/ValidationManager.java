package org.commonjava.redhat.maven.rv.mgr;

import static org.commonjava.redhat.maven.rv.util.AnnotationUtils.findNamed;
import static org.commonjava.redhat.maven.rv.util.ArtifactReferenceUtils.toArtifactRef;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.DefaultRepositoryRequest;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataResolutionException;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.common.version.InvalidVersionSpecificationException;
import org.apache.maven.graph.common.version.VersionSpec;
import org.apache.maven.graph.common.version.VersionUtils;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
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
import org.commonjava.redhat.maven.rv.report.ValidationReport;
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
    private RepositoryMetadataManager repoMetadataManager;

    @Inject
    private RepositorySystem repoSystem;

    @Inject
    private SessionInitializer sessionInitializer;

    @Inject
    private Instance<ValidationReport> reports;

    public void validate( final ValidatorSession session )
        throws ValidationException
    {
        session.initializeMavenComponents( sessionInitializer, repoSystem );

        processPomFiles( session );
        processReferencedArtifacts( session );

        logger.info( "Writing reports..." );
        // TODO: Report errors encountered and logged in session!
        int reportsWritten = 0;
        int reportsFailed = 0;
        for ( final ValidationReport report : reports )
        {
            final String named = findNamed( report );
            logger.info( "...writing %s", named );

            try
            {
                report.write( session );
                reportsWritten++;
            }
            catch ( final IOException e )
            {
                logger.error( "Failed to write report: %s.\nError: %s", e, named, e.getMessage() );
                reportsFailed++;
            }
            catch ( final ValidationException e )
            {
                logger.error( "Failed to write report: %s.\nError: %s", e, named, e.getMessage() );
                reportsFailed++;
            }
            // just to be safe...
            catch ( final Error e )
            {
                logger.error( "Failed to write report: %s.\nError: %s", e, named, e.getMessage() );
                reportsFailed++;
            }
        }

        final long total = Runtime.getRuntime()
                                  .totalMemory();
        final long max = Runtime.getRuntime()
                                .maxMemory();

        final String totalMem = ( total / ( 1024 * 1024 ) ) + "M";
        final String maxMem = ( max / ( 1024 * 1024 ) ) + "M";

        logger.info( "\n\n\nSummary:\n-----------------\n  Processed %d POMs\n  %d Reports written\n  %d Reports failed!\n  Memory Usage: %s / %s\n\n",
                     session.getSeen()
                            .size(), reportsWritten, reportsFailed, totalMem, maxMem );
    }

    private void processReferencedArtifacts( final ValidatorSession session )
    {
        ArtifactRef artiRef = null;
        while ( ( artiRef = session.getNextArtifactToResolve() ) != null )
        {
            resolveArtifact( artiRef, session );
        }
    }

    private void processPomFiles( final ValidatorSession session )
    {
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
            if ( !pomFile.exists() )
            {
                continue;
            }

            final Model model = buildModel( pomFile, session );

            if ( model != null )
            {
                logger.info( "Validating: %s", pom );
                validateProjectGraph( model, session );
            }
        }

        ProjectVersionRef ref = null;
        while ( ( ref = session.getNextToProjectResolve() ) != null )
        {
            if ( session.hasSeen( ref ) )
            {
                continue;
            }

            logger.info( "\n\nValidating: %s\n\n", ref );

            final ModelSource source = resolveModel( ref, session );
            if ( source == null )
            {
                session.addMissing( ref );
                continue;
            }

            final Model model = buildModel( source, session );
            if ( model == null )
            {
                session.addMissing( ref );
                continue;
            }

            validateProjectGraph( model, session );
        }
    }

    private ModelSource resolveModel( ProjectVersionRef ref, final ValidatorSession session )
    {
        if ( ref instanceof ArtifactRef )
        {
            ref = ( (ArtifactRef) ref ).asProjectVersionRef();
        }

        if ( ref.isCompound() )
        {
            ref = resolveVersionRange( ref, session );

            if ( ref == null )
            {
                return null;
            }
        }

        if ( session.isMissing( ref ) )
        {
            logger.info( "%s is already marked as missing. Skipping.", ref );
            return null;
        }
        else
        {
            logger.info( "Resolving POM for %s", ref );
        }

        ModelSource source = null;
        try
        {
            // FIXME: Resolve version ranges before attempting this.
            // FIXME: Once version ranges are resolved, we'll need a decent way to log the seen/missing status for this ref.
            source = session.getModelResolver()
                            .resolveModel( ref.getGroupId(), ref.getArtifactId(), ref.getVersionSpec()
                                                                                     .renderStandard() );
        }
        catch ( final UnresolvableModelException e )
        {
            logger.info( "Failed to resolve: %s, Error was: %s", e, ref, e.getMessage() );
            session.addError( ref, e );

            logger.info( "Marking missing: %s[%s]", ref.getClass()
                                                       .getName(), ref );
            session.addMissing( ref );
        }

        return source;
    }

    private ProjectVersionRef resolveVersionRange( final ProjectVersionRef ref, final ValidatorSession session )
    {
        final VersionSpec versionSpec = ref.getVersionSpec();
        final Artifact dummy = repoSystem.createProjectArtifact( ref.getGroupId(), ref.getArtifactId(), "1" );
        final ArtifactRepositoryMetadata mdWrapper = new ArtifactRepositoryMetadata( dummy );

        final DefaultRepositoryRequest drr = new DefaultRepositoryRequest( session.getBaseArtifactResolutionRequest() );
        VersionSpec resolved = null;
        try
        {
            repoMetadataManager.resolve( mdWrapper, drr );

            final Metadata metadata = mdWrapper.getMetadata();
            final Versioning versioning = metadata.getVersioning();
            if ( versioning != null )
            {
                final List<VersionSpec> versions = new ArrayList<VersionSpec>();
                for ( final String v : versioning.getVersions() )
                {
                    try
                    {
                        final VersionSpec ver = VersionUtils.createSingleVersion( v );
                        versions.add( ver );
                    }
                    catch ( final InvalidVersionSpecificationException e )
                    {
                        logger.warn( "Invalid version encountered: '%s' while resolving range: %s in POM reference: %s. Reason: %s\n\nSkipping it...\n\n",
                                     e, v, versionSpec.renderStandard(), ref, e.getMessage() );
                    }
                }

                Collections.sort( versions );
                Collections.reverse( versions );
                for ( final VersionSpec version : versions )
                {
                    if ( versionSpec.contains( version ) )
                    {
                        resolved = version;
                        break;
                    }
                }
            }

        }
        catch ( final RepositoryMetadataResolutionException e )
        {
            logger.error( "Failed to resolve versions for range: %s in POM reference: %s. Reason: %s", e, versionSpec,
                          ref, e.getMessage() );
            session.addError( ref, e );
            session.addVersionResolutionFailure( ref );
        }

        if ( resolved == null )
        {
            session.addVersionResolutionFailure( ref );
        }

        return resolved == null ? null : new ProjectVersionRef( ref.getGroupId(), ref.getArtifactId(), resolved );
    }

    private Model buildModel( final File pomFile, final ValidatorSession session )
    {
        final ModelSource source = new FileModelSource( pomFile );
        final Model model = buildModel( source, session );

        return model;
    }

    private Model buildModel( final ModelSource source, final ValidatorSession session )
    {
        final DefaultModelBuildingRequest mbr =
            new DefaultModelBuildingRequest( session.getBaseModelBuildingRequest() ).setModelSource( source )
                                                                                    .setModelResolver( session.getModelResolver() );

        Model model = null;
        Model raw = null;
        ProjectVersionRef ref = null;
        try
        {
            final ModelBuildingResult result = modelBuilder.build( mbr );
            model = result.getEffectiveModel();
            raw = result.getRawModel();

            if ( model == null )
            {
                raw = readRawModel( source, session );
                if ( raw != null )
                {
                    ref = toArtifactRef( raw, session );
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
            raw = readRawModel( source, session );
            if ( raw != null )
            {
                ref = toArtifactRef( raw, session );
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

                session.addMissing( ref );
            }

            session.addLowLevelError( new ValidationException( "Failed to build Model for POM: %s. Reason: %s", e,
                                                               source.getLocation(), e.getMessage() ) );
        }

        if ( model != null )
        {
            session.addSeen( toArtifactRef( model, session ) );
        }

        if ( raw != null && ref != null )
        {
            final Parent parent = raw.getParent();
            if ( parent != null )
            {
                final ProjectVersionRef parentRef = toArtifactRef( parent, session );
                session.addParentLink( ref, parentRef );
            }

            logger.info( "Looking for BOM imports in raw model: %s", ref );

            // We have to process import-scoped deps from the raw model, 
            // since BOM references are REPLACED by their dependencyManagement contents
            // when the model is loaded, making them invisible once the effective
            // model is computed.
            if ( raw.getDependencyManagement() != null && raw.getDependencyManagement()
                                                             .getDependencies() != null )
            {
                final List<Dependency> extraToValidate = new ArrayList<Dependency>();

                final List<Dependency> managed = raw.getDependencyManagement()
                                                    .getDependencies();
                for ( final Dependency dep : managed )
                {
                    if ( "import".equals( dep.getScope() ) && "pom".equals( dep.getType() ) )
                    {
                        final ArtifactRef depRef = toArtifactRef( dep, ref, session );
                        if ( depRef == null )
                        {
                            logger.error( "Cannot add BOM reference for %s. Error occurred while constructing artifact reference",
                                          dep );
                            continue;
                        }

                        logger.info( "Adding BOM reference: %s", depRef );

                        session.addBom( depRef.asProjectVersionRef() );

                        extraToValidate.add( dep );
                    }
                }

                if ( !extraToValidate.isEmpty() )
                {
                    validateDependencies( extraToValidate, session, true, ref );
                }
            }
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

    private void resolveArtifact( final ArtifactRef ref, final ValidatorSession session )
    {
        logger.info( "Resolving: %s", ref );
        final ArtifactResolutionRequest req =
            new ArtifactResolutionRequest( session.getBaseArtifactResolutionRequest() );

        req.setArtifact( repoSystem.createArtifact( ref.getGroupId(), ref.getArtifactId(), ref.getVersionSpec()
                                                                                              .renderStandard(),
                                                    ref.getType() ) );

        final ArtifactResolutionResult result = repoSystem.resolve( req );

        final List<Exception> exceptions = result.getExceptions();
        if ( exceptions != null && !exceptions.isEmpty() )
        {
            session.addMissing( ref );
            for ( final Exception exception : exceptions )
            {
                session.addError( ref.asProjectVersionRef(), exception );
            }
        }
    }

    private void validateProjectGraph( final Model model, final ValidatorSession session )
    {
        logger.info( "Validating project references for: %s", model );

        final ProjectVersionRef src = toArtifactRef( model, session );
        if ( src == null )
        {
            logger.error( "Model '%s' has an invalid version, or otherwise cannot used to create a ProjectVersionRef.",
                          model );
            return;
        }

        validateDependencySections( model, session, src );
        validateBuild( model.getBuild(), session, src );
        validateReporting( model, session, src );

        // FIXME: Not sure what to do with profiles. 
        // I suspect checking them exhaustively will result in a lot of 
        // irrelevant results...

        //        final List<Profile> profiles = model.getProfiles();
        //        if ( profiles != null )
        //        {
        //            final Map<ProjectRef, Dependency> managed = new HashMap<ProjectRef, Dependency>();
        //            if ( model.getDependencyManagement() != null && model.getDependencyManagement()
        //                                                                 .getDependencies() != null )
        //            {
        //                for ( final Dependency d : model.getDependencyManagement()
        //                                                .getDependencies() )
        //                {
        //                    final ProjectRef ref = new ProjectRef( d.getGroupId(), d.getArtifactId() );
        //                    if ( !managed.containsKey( ref ) )
        //                    {
        //                        managed.put( ref, d );
        //                    }
        //                }
        //            }
        //
        //            for ( final Profile profile : profiles )
        //            {
        //                logger.info( "Validating profile: %s", profile.getId() );
        //                validateDependencySections( profile, session, managed, src );
        //                validateBuild( profile.getBuild(), session, src );
        //                validateReporting( profile, session, src );
        //            }
        //        }
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
        //        logger.info( "Validating reporting: %s", src );
        final Reporting reporting = model.getReporting();
        if ( reporting != null )
        {
            final List<ReportPlugin> plugins = reporting.getPlugins();
            if ( plugins != null )
            {
                int idx = 0;
                for ( final ReportPlugin plugin : plugins )
                {
                    ProjectRef ref = toArtifactRef( plugin, src, session );
                    if ( ref == null )
                    {
                        continue;
                    }

                    if ( !( ref instanceof ProjectVersionRef ) )
                    {
                        logger.info( "Resolving version for: %s", ref );
                        ref = resolvePluginVersion( ref, session, src );
                    }
                    else if ( ( (ProjectVersionRef) ref ).isCompound() )
                    {
                        final ProjectVersionRef projectRef = resolveVersionRange( (ProjectVersionRef) ref, session );

                        if ( projectRef == null )
                        {
                            //                            logger.info( "Failed to resolve version for range: %s in plugin: %s of: %s",
                            //                                         ( (ProjectVersionRef) ref ).getVersionSpec()
                            //                                                                    .renderStandard(), ref, src );
                            session.addMissing( (ProjectVersionRef) ref );
                            continue;
                        }
                        else
                        {
                            ref = projectRef;
                        }
                    }

                    if ( ref != null )
                    {
                        if ( session.hasSeen( (ProjectVersionRef) ref ) )
                        {
                            continue;
                        }

                        session.addPluginLink( src, (ProjectVersionRef) ref, idx, false, true );
                        session.addArtifactToResolve( (ProjectVersionRef) ref, "maven-plugin" );
                    }

                    idx++;
                }
            }
        }
    }

    private void validatePlugins( final List<Plugin> plugins, final ValidatorSession session, final boolean managed,
                                  final ProjectVersionRef src )
    {
        //        logger.info( "Validating plugins: %s", src );
        if ( plugins != null )
        {
            int idx = 0;
            for ( final Plugin plugin : plugins )
            {
                ProjectRef ref = toArtifactRef( plugin, src, session );
                if ( ref == null )
                {
                    continue;
                }

                if ( !( ref instanceof ProjectVersionRef ) )
                {
                    logger.info( "Resolving version for: %s", ref );
                    ref = resolvePluginVersion( ref, session, src );
                }
                else if ( ( (ProjectVersionRef) ref ).isCompound() )
                {
                    final ProjectVersionRef projectRef = resolveVersionRange( (ProjectVersionRef) ref, session );

                    if ( projectRef == null )
                    {
                        //                        logger.info( "Failed to resolve version for range: %s in plugin: %s of: %s",
                        //                                     ( (ProjectVersionRef) ref ).getVersionSpec()
                        //                                                                .renderStandard(), ref, src );
                        session.addMissing( (ProjectVersionRef) ref );
                        continue;
                    }
                    else
                    {
                        ref = projectRef;
                    }
                }

                if ( ref != null )
                {
                    if ( session.hasSeen( (ProjectVersionRef) ref ) )
                    {
                        continue;
                    }

                    session.addPluginLink( src, (ProjectVersionRef) ref, idx, managed, false );
                    session.addArtifactToResolve( (ProjectVersionRef) ref, "maven-plugin" );
                }

                idx++;
            }
        }
    }

    private void validateExtensions( final List<Extension> extensions, final ValidatorSession session,
                                     final ProjectVersionRef src )
    {
        //        logger.info( "Validating extensions: %s", src );
        if ( extensions != null )
        {
            int idx = 0;
            for ( final Extension extension : extensions )
            {
                final ProjectVersionRef ref = toArtifactRef( extension, src, session );
                if ( ref == null )
                {
                    continue;
                }

                if ( session.hasSeen( ref ) )
                {
                    continue;
                }

                session.addExtensionLink( src, ref, idx );
                session.addArtifactToResolve( ref, "jar" );

                idx++;
            }
        }
    }

    private void validateDependencies( final List<Dependency> deps, final ValidatorSession session,
                                       final boolean managed, final ProjectVersionRef src )
    {
        //        logger.info( "Validating dependencies: %s", src );
        if ( deps != null )
        {
            int idx = 0;
            for ( final Dependency dependency : deps )
            {
                //                logger.info( "[DEP] %s", dependency );

                ArtifactRef ref = toArtifactRef( dependency, src, session );
                if ( ref == null )
                {
                    continue;
                }

                if ( ref.isCompound() )
                {
                    final ProjectVersionRef projectRef = resolveVersionRange( ref, session );

                    if ( projectRef == null )
                    {
                        //                        logger.info( "Failed to resolve version for range: %s in dependency: %s of: %s",
                        //                                     ref.getVersionSpec()
                        //                                        .renderStandard(), ref, src );
                        session.addMissing( ref );
                        continue;
                    }
                    else
                    {
                        ref = new ArtifactRef( projectRef, ref.getType(), ref.getClassifier(), ref.isOptional() );
                    }
                }

                if ( session.hasSeen( ref.asProjectVersionRef() ) )
                {
                    continue;
                }

                session.addDependencyLink( src, ref, DependencyScope.getScope( dependency.getScope() ), idx, managed );
                session.addArtifactToResolve( ref, dependency.getType() );

                idx++;
            }
        }
    }

    //    private void validateModelAndArtifact( final ProjectVersionRef base, final String type,
    //                                           final ValidatorSession session, final ProjectVersionRef src )
    //    {
    //        ProjectVersionRef pom = base;
    //        if ( pom instanceof ArtifactRef )
    //        {
    //            pom = ( (ArtifactRef) pom ).asProjectVersionRef();
    //        }
    //
    //        logger.info( "Checking missing: %s[%s]", pom.getClass()
    //                                                    .getName(), pom );
    //        if ( session.isMissing( pom ) )
    //        {
    //            return;
    //        }
    //
    //        final ArtifactRef artifact = toArtifactRef( pom, type, session );
    //
    //        logger.info( "Validating model: %s with artifact of type: %s (referenced from: %s)", pom, type, src );
    //
    //        if ( !session.hasSeen( pom ) )
    //        {
    //            logger.info( "Building model for: %s", pom );
    //
    //            // build the model
    //            final ModelSource source = resolveModel( pom, session, src );
    //            if ( source != null )
    //            {
    //                logger.info( "Resolved to: %s", source.getLocation() );
    //                final Model model = buildModel( source, session, source.getLocation() );
    //
    //                if ( model != null )
    //                {
    //                    session.addSeen( pom );
    //
    //                    // validate the project graph for the plugin
    //                    validateProjectGraph( model, session );
    //                }
    //                else
    //                {
    //                    logger.info( "Marking missing: %s[%s]", pom.getClass()
    //                                                               .getName(), pom );
    //                    session.addMissing( pom );
    //                }
    //            }
    //        }
    //
    //        if ( !session.hasSeen( artifact ) )
    //        {
    //            logger.info( "Resolving: %s:%s", artifact, type );
    //
    //            // resolve the jar (maven-plugin type)
    //            resolveArtifact( artifact, session, src );
    //        }
    //    }

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
            session.addError( src, e );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addLowLevelError( new ValidationException(
                                                               "Failed to parse version: '%s'\nPlugin: %s\nPOM: %s\nReason: %s",
                                                               e, version, ref, src, e.getMessage() ) );
        }

        session.addVersionResolutionFailure( ref );

        return null;
    }

}
