package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.PluginDependencyRelationship;
import org.apache.maven.graph.effective.rel.PluginRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.RelationshipDeclarationComparator;

public abstract class AbstractInverseRelationshipReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            final EProjectWeb web = session.getProjectWeb();
            final Set<ProjectVersionRef> processed = new HashSet<ProjectVersionRef>();

            for ( ProjectVersionRef ref : getReferencesToReport( session ) )
            {
                if ( ref instanceof ArtifactRef )
                {
                    ref = ( (ArtifactRef) ref ).asProjectVersionRef();
                }

                if ( processed.contains( ref ) )
                {
                    continue;
                }

                Set<ProjectRelationship<?>> userRelationships = web.getUserRelationships( ref );
                userRelationships = filterRelationships( userRelationships );

                final List<String> digests = digest( userRelationships );

                if ( digests.isEmpty() )
                {
                    continue;
                }

                writer.printf( "\n\n%s\n-------------------------------------\n\n  %s", ref, join( digests, "\n  " ) );

                processed.add( ref );
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    protected Set<ProjectRelationship<?>> filterRelationships( final Set<ProjectRelationship<?>> rels )
    {
        return rels;
    }

    protected abstract Set<ProjectVersionRef> getReferencesToReport( ValidatorSession session );

    // TODO: Find a better way of dealing with managed information. 
    // We need to report it somehow, since BOM imports can have a strong effect 
    // on downstream users. I'm just not yet sure how to keep it from appearing 
    // repeatedly.
    private List<String> digest( final Set<ProjectRelationship<?>> userRelationships )
    {
        final List<String> result = new ArrayList<String>();

        final List<ProjectRelationship<?>> sorted = new ArrayList<ProjectRelationship<?>>( userRelationships );
        Collections.sort( sorted, new RelationshipDeclarationComparator() );

        for ( final ProjectRelationship<?> rel : sorted )
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( "by " )
              .append( rel.getDeclaring() )
              .append( " (" );

            switch ( rel.getType() )
            {
                case DEPENDENCY:
                {
                    final DependencyRelationship dr = (DependencyRelationship) rel;

                    // NOTE: if we use BOMs widely, this will produce a strong 
                    // irrelevant signal, as the BOM inclusion will make it look 
                    // like everything uses everything in the BOM.
                    if ( dr.isManaged() )
                    {
                        continue;
                    }

                    sb.append( "dependency; scope: " )
                      .append( dr.getScope() )
                      .append( ", optional: " )
                      .append( dr.getTarget()
                                 .isOptional() );
                    break;
                }
                case PLUGIN:
                {
                    final PluginRelationship pr = (PluginRelationship) rel;

                    // NOTE: if we use a toolchain parent POM, this will produce 
                    // a strong irrelevant signal, as the toolchain inheritance  
                    // will make it look like everything uses all the managed plugins.
                    if ( pr.isManaged() )
                    {
                        continue;
                    }

                    sb.append( "plugin" );
                    break;
                }
                case PLUGIN_DEP:
                {
                    final PluginDependencyRelationship pdr = (PluginDependencyRelationship) rel;
                    sb.append( "plugin-dependency; plugin: " )
                      .append( pdr.getPlugin() );
                    break;
                }
                default:
                {
                    sb.append( rel.getType()
                                  .name()
                                  .toLowerCase() );
                }
            }

            sb.append( ")" );
            result.add( sb.toString() );
        }
        return result;
    }

    public boolean canRun( final ValidatorSession session )
    {
        return session.getProjectWeb() != null && getReferencesToReport( session ) != null;
    }

}
