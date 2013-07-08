package org.commonjava.redhat.maven.rv.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.common.version.InvalidVersionSpecificationException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.util.logging.Logger;

public final class ArtifactReferenceUtils
{

    private static final Logger logger = new Logger( ArtifactReferenceUtils.class );

    private ArtifactReferenceUtils()
    {
    }

    public static ProjectVersionRef toArtifactRef( final Model model, final ValidatorSession session )
    {
        try
        {
            String group = model.getGroupId();
            final Parent parent = model.getParent();
            if ( group == null && parent != null )
            {
                group = parent.getGroupId();
            }
            String version = model.getVersion();
            if ( version == null && parent != null )
            {
                version = parent.getVersion();
            }

            if ( group == null || version == null )
            {
                logger.error( "Invalid POM: %s:%s:%s", group, model.getArtifactId(), version );
                session.addLowLevelError( new ValidationException(
                                                                   "Invalid POM coordinate (missing information): %s:%s:%s.",
                                                                   group, model.getArtifactId(), version ) );
                return null;
            }

            final ProjectVersionRef ref = new ProjectVersionRef( group, model.getArtifactId(), version );

            // Trigger version spec exception...
            ref.getVersionSpec();

            return ref;
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            logger.error( "Cannot parse version for %s. Reason: %s", e, model, e.getMessage() );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s. Reason: %s", e, model,
                                                               e.getMessage() ) );
        }
        //        catch ( final IllegalArgumentException e )
        //        {
        //            logger.error( "Cannot parse version for %s. Reason: %s", e, model, e.getMessage() );
        //            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s. Reason: %s", e, model,
        //                                                               e.getMessage() ) );
        //        }

        return null;
    }

    public static ProjectVersionRef toArtifactRef( final Parent parent, final ValidatorSession session )
    {
        try
        {
            final ProjectVersionRef ref =
                new ProjectVersionRef( parent.getGroupId(), parent.getArtifactId(), parent.getVersion() );

            // Trigger version spec exception...
            ref.getVersionSpec();

            return ref;
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            logger.error( "Cannot parse version for %s. Reason: %s", e, parent, e.getMessage() );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s. Reason: %s", e, parent,
                                                               e.getMessage() ) );
        }

        return null;
    }

    public static ProjectVersionRef toArtifactRef( final Extension ext, final ProjectVersionRef src,
                                                   final ValidatorSession session, final Model model )
    {
        ProjectVersionRef ref = null;
        try
        {
            ref =
                new ProjectVersionRef( resolveExpressions( ext.getGroupId(), model ), ext.getArtifactId(),
                                       resolveExpressions( ext.getVersion(), model ) );

            // Trigger version spec exception...
            ref.getVersionSpec();
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            logger.error( "Cannot parse version for %s in %s. Reason: %s", e, ext, src, e.getMessage() );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               ext, src, e.getMessage() ) );
        }
        //        catch ( final IllegalArgumentException e )
        //        {
        //            logger.error( "Cannot parse version for %s in %s. Reason: %s", e, ext, src, e.getMessage() );
        //            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
        //                                                               ext, src, e.getMessage() ) );
        //        }

        return ref;
    }

    public static ArtifactRef toArtifactRef( final Dependency dep, final ProjectVersionRef src,
                                             final ValidatorSession session, final Model model )
    {
        ArtifactRef ref = null;
        try
        {
            ref =
                new ArtifactRef( new ProjectVersionRef( resolveExpressions( dep.getGroupId(), model ),
                                                        dep.getArtifactId(), resolveExpressions( dep.getVersion(),
                                                                                                 model ) ),
                                 dep.getType(), dep.getClassifier(), dep.isOptional() );

            // Trigger version spec exception...
            ref.getVersionSpec();
        }
        //        catch ( final IllegalArgumentException e )
        //        {
        //            logger.error( "Cannot parse version for %s in %s. Reason: %s", e, dep, src, e.getMessage() );
        //            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
        //                                                               dep, src, e.getMessage() ) );
        //        }
        catch ( final InvalidVersionSpecificationException e )
        {
            logger.error( "Cannot parse version for %s in %s. Reason: %s", e, dep, src, e.getMessage() );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               dep, src, e.getMessage() ) );
        }

        return ref;
    }

    private static String resolveExpressions( final String raw, final Model model )
    {
        if ( raw == null )
        {
            return raw;
        }

        if ( raw.contains( "${" ) )
        {
            final StringSearchInterpolator interp = new StringSearchInterpolator();
            interp.addValueSource( new PropertiesBasedValueSource( model.getProperties() ) );

            final List<String> expressionRoots = new ArrayList<String>();
            expressionRoots.add( "pom." );
            expressionRoots.add( "project." );

            interp.addValueSource( new PrefixedObjectValueSource( expressionRoots, model, true ) );

            try
            {
                return interp.interpolate( raw );
            }
            catch ( final InterpolationException e )
            {
                logger.error( "Failed to resolve expression from model.\nRaw string: '%s'\nModel: %s\nError: %s", e,
                              raw, model, e.getMessage() );
            }
        }

        return raw;
    }

    public static ArtifactRef toArtifactRef( final ProjectVersionRef base, final String type,
                                             final ValidatorSession session )
    {
        try
        {
            final ArtifactRef ref =
                new ArtifactRef(
                                 new ProjectVersionRef( base.getGroupId(), base.getArtifactId(), base.getVersionSpec() ),
                                 type, null, false );

            return ref;
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            logger.error( "Cannot parse version for %s in %s. Reason: %s", e, type, base, e.getMessage() );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               type, base, e.getMessage() ) );
        }

        return null;
    }

    public static ProjectRef toArtifactRef( final Plugin plugin, final ProjectVersionRef src,
                                            final ValidatorSession session, final Model model )
    {
        ProjectRef ref = null;
        if ( plugin.getVersion() == null )
        {
            ref = new ProjectRef( resolveExpressions( plugin.getGroupId(), model ), plugin.getArtifactId() );
        }
        else
        {
            try
            {
                ref =
                    new ProjectVersionRef( resolveExpressions( plugin.getGroupId(), model ), plugin.getArtifactId(),
                                           resolveExpressions( plugin.getVersion(), model ) );

                // Trigger version spec exception...
                ( (ProjectVersionRef) ref ).getVersionSpec();
            }
            catch ( final InvalidVersionSpecificationException e )
            {
                logger.error( "Cannot parse version for %s in %s. Reason: %s", e, plugin, src, e.getMessage() );
                session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                                   plugin, src, e.getMessage() ) );
            }
            //            catch ( final IllegalArgumentException e )
            //            {
            //                logger.error( "Cannot parse version for %s in %s. Reason: %s", e, plugin, src, e.getMessage() );
            //                session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
            //                                                                   plugin, src, e.getMessage() ) );
            //            }
        }

        return ref;
    }

    public static ProjectRef toArtifactRef( final ReportPlugin plugin, final ProjectVersionRef src,
                                            final ValidatorSession session, final Model model )
    {
        ProjectRef ref = null;
        if ( plugin.getVersion() == null )
        {
            ref = new ProjectRef( plugin.getGroupId(), plugin.getArtifactId() );
        }
        else
        {
            try
            {
                ref =
                    new ProjectVersionRef( resolveExpressions( plugin.getGroupId(), model ), plugin.getArtifactId(),
                                           resolveExpressions( plugin.getVersion(), model ) );

                // Trigger version spec exception...
                ( (ProjectVersionRef) ref ).getVersionSpec();
            }
            catch ( final InvalidVersionSpecificationException e )
            {
                logger.error( "Cannot parse version for %s in %s. Reason: %s", e, plugin, src, e.getMessage() );
                session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                                   plugin, src, e.getMessage() ) );
            }
            //            catch ( final IllegalArgumentException e )
            //            {
            //                logger.error( "Cannot parse version for %s in %s. Reason: %s", e, plugin, src, e.getMessage() );
            //                session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
            //                                                                   plugin, src, e.getMessage() ) );
            //            }
        }

        return ref;
    }

}
