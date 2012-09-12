package org.commonjava.redhat.maven.rv.model;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.common.version.InvalidVersionSpecificationException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

public final class ArtifactReferenceUtils
{

    private ArtifactReferenceUtils()
    {
    }

    public static ProjectVersionRef toArtifactRef( final Model model, final ValidatorSession session )
    {
        ProjectVersionRef ref = null;
        try
        {
            ref = new ProjectVersionRef( model.getGroupId(), model.getArtifactId(), model.getVersion() );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s. Reason: %s", e, model,
                                                               e.getMessage() ) );
        }

        return ref;
    }

    public static ProjectVersionRef toArtifactRef( final Extension ext, final ProjectVersionRef src,
                                                   final ValidatorSession session )
    {
        ProjectVersionRef ref = null;
        try
        {
            ref = new ProjectVersionRef( ext.getGroupId(), ext.getArtifactId(), ext.getVersion() );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               ext, src, e.getMessage() ) );
        }

        return ref;
    }

    public static ArtifactRef toArtifactRef( final Dependency dep, final ProjectVersionRef src,
                                             final ValidatorSession session )
    {
        ArtifactRef ref = null;
        try
        {
            ref =
                new ArtifactRef( new ProjectVersionRef( dep.getGroupId(), dep.getArtifactId(), dep.getVersion() ),
                                 dep.getType(), dep.getClassifier(), dep.isOptional() );
        }
        catch ( final IllegalArgumentException e )
        {
            if ( dep.getVersion() == null )
            {
                session.addLowLevelError( new ValidationException( "Missing version for: %s in %s. Reason: %s", e, dep,
                                                                   src, e.getMessage() ) );
            }
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                               dep, src, e.getMessage() ) );
        }

        return ref;
    }

    public static ArtifactRef toArtifactRef( final ProjectVersionRef base, final String type,
                                             final ValidatorSession session )
    {
        return new ArtifactRef(
                                new ProjectVersionRef( base.getGroupId(), base.getArtifactId(), base.getVersionSpec() ),
                                type, null, false );
    }

    public static ProjectRef toArtifactRef( final Plugin plugin, final ProjectVersionRef src,
                                            final ValidatorSession session )
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
                ref = new ProjectVersionRef( plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion() );
            }
            catch ( final InvalidVersionSpecificationException e )
            {
                session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                                   plugin, src, e.getMessage() ) );
            }
        }

        return ref;
    }

    public static ProjectRef toArtifactRef( final ReportPlugin plugin, final ProjectVersionRef src,
                                            final ValidatorSession session )
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
                ref = new ProjectVersionRef( plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion() );
            }
            catch ( final InvalidVersionSpecificationException e )
            {
                session.addLowLevelError( new ValidationException( "Cannot parse version for: %s in %s. Reason: %s", e,
                                                                   plugin, src, e.getMessage() ) );
            }
        }

        return ref;
    }

}
