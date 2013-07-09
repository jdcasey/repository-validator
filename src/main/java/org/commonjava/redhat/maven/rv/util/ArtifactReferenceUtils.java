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

public final class ArtifactReferenceUtils
{

    //    private static final Logger logger = new Logger( ArtifactReferenceUtils.class );

    private ArtifactReferenceUtils()
    {
    }

    public static ProjectVersionRef toArtifactRef( final Model model, final ValidatorSession session )
    {
        String group = model.getGroupId();
        try
        {
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
                //                logger.error( "Invalid POM: %s:%s:%s", group, model.getArtifactId(), version );
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
        catch ( final NullPointerException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( group, model.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s. Reason: %s", e, model,
                                                               e.getMessage() ) );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( group, model.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s. Reason: %s", e, model,
                                                               e.getMessage() ) );
        }

        return null;
    }

    public static ProjectVersionRef toArtifactRef( final Parent parent, final Object src, final ValidatorSession session )
    {
        try
        {
            final ProjectVersionRef ref =
                new ProjectVersionRef( parent.getGroupId(), parent.getArtifactId(), parent.getVersion() );

            // Trigger version spec exception...
            ref.getVersionSpec();

            return ref;
        }
        catch ( final NullPointerException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( parent.getGroupId(), parent.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               parent, src, e.getMessage() ) );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( parent.getGroupId(), parent.getArtifactId() ) );
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
                new ProjectVersionRef( resolveExpressions( ext.getGroupId(), session, model ), ext.getArtifactId(),
                                       resolveExpressions( ext.getVersion(), session, model ) );

            // Trigger version spec exception...
            ref.getVersionSpec();

            return ref;
        }
        catch ( final NullPointerException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( ext.getGroupId(), ext.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               ext, src, e.getMessage() ) );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( ext.getGroupId(), ext.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               ext, src, e.getMessage() ) );
        }
        catch ( final InterpolationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot resolve expressions for: %s in %s. Reason: %s",
                                                               e, ext, model, e.getMessage() ) );
        }

        return null;
    }

    public static ArtifactRef toArtifactRef( final Dependency dep, final ProjectVersionRef src,
                                             final ValidatorSession session, final Model model )
    {
        ArtifactRef ref = null;
        try
        {
            ref =
                new ArtifactRef( new ProjectVersionRef( resolveExpressions( dep.getGroupId(), session, model ),
                                                        dep.getArtifactId(), resolveExpressions( dep.getVersion(),
                                                                                                 session, model ) ),
                                 dep.getType(), dep.getClassifier(), dep.isOptional() );

            // Trigger version spec exception...
            ref.getVersionSpec();

            return ref;
        }
        catch ( final NullPointerException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( dep.getGroupId(), dep.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               dep, src, e.getMessage() ) );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( dep.getGroupId(), dep.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               dep, src, e.getMessage() ) );
        }
        catch ( final InterpolationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot resolve expressions for: %s in %s. Reason: %s",
                                                               e, dep, model, e.getMessage() ) );
        }

        return null;
    }

    private static String resolveExpressions( final String raw, final ValidatorSession session, final Model model )
        throws InterpolationException
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

            return interp.interpolate( raw );
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
            session.addVersionResolutionFailure( base.asProjectRef() );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               type, base, e.getMessage() ) );
        }

        return null;
    }

    public static ProjectRef toArtifactRef( final Plugin plugin, final ProjectVersionRef src,
                                            final ValidatorSession session, final Model model )
    {
        try
        {
            ProjectRef ref = null;
            if ( plugin.getVersion() == null )
            {
                if ( plugin.getGroupId() != null && plugin.getArtifactId() != null )
                {
                    ref =
                        new ProjectRef( resolveExpressions( plugin.getGroupId(), session, model ),
                                        plugin.getArtifactId() );
                }
            }
            else
            {
                ref =
                    new ProjectVersionRef( resolveExpressions( plugin.getGroupId(), session, model ),
                                           plugin.getArtifactId(), resolveExpressions( plugin.getVersion(), session,
                                                                                       model ) );

                // Trigger version spec exception...
                ( (ProjectVersionRef) ref ).getVersionSpec();
            }

            return ref;
        }
        catch ( final NullPointerException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( plugin.getGroupId(), plugin.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               plugin, src, e.getMessage() ) );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( plugin.getGroupId(), plugin.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               plugin, src, e.getMessage() ) );
        }
        catch ( final InterpolationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot resolve expressions for: %s in %s. Reason: %s",
                                                               e, plugin, model, e.getMessage() ) );
        }

        return null;
    }

    public static ProjectRef toArtifactRef( final ReportPlugin plugin, final ProjectVersionRef src,
                                            final ValidatorSession session, final Model model )
    {
        try
        {
            ProjectRef ref = null;
            if ( plugin.getVersion() == null )
            {
                if ( plugin.getGroupId() != null && plugin.getArtifactId() != null )
                {
                    ref =
                        new ProjectRef( resolveExpressions( plugin.getGroupId(), session, model ),
                                        plugin.getArtifactId() );
                }
            }
            else
            {
                ref =
                    new ProjectVersionRef( resolveExpressions( plugin.getGroupId(), session, model ),
                                           plugin.getArtifactId(), resolveExpressions( plugin.getVersion(), session,
                                                                                       model ) );

                // Trigger version spec exception...
                ( (ProjectVersionRef) ref ).getVersionSpec();
            }

            return ref;
        }
        catch ( final NullPointerException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( plugin.getGroupId(), plugin.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               plugin, src, e.getMessage() ) );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addVersionResolutionFailure( new ProjectRef( plugin.getGroupId(), plugin.getArtifactId() ) );
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               plugin, src, e.getMessage() ) );
        }
        catch ( final InterpolationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot resolve expressions for: %s in %s. Reason: %s",
                                                               e, plugin, model, e.getMessage() ) );
        }

        return null;
    }

}
