package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.apache.maven.graph.effective.rel.RelationshipComparator;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "boms.txt" )
public class BomsReport
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

            final Set<ProjectVersionRef> boms = session.getBoms();
            for ( final ProjectVersionRef bom : boms )
            {
                final Set<ProjectRelationship<?>> rels = web.getDirectRelationships( bom );
                final List<DependencyRelationship> digested = filterAndSort( rels );

                writer.printf( "\n\n%s:\n-------------------------------------------------\n", bom );

                if ( !digested.isEmpty() )
                {
                    for ( final DependencyRelationship rel : digested )
                    {
                        final ArtifactRef target = rel.getTarget();
                        final boolean pomMissing = session.isMissing( target.asProjectVersionRef() );
                        final boolean artMissing = session.isMissing( target );

                        writer.printf( "\n  %d. %s (scope: %s)\n    POM:      %s\n    Artifact: %s", rel.getIndex(),
                                       target, rel.getScope(), ( pomMissing ? "MISSING" : "OK" ),
                                       ( artMissing ? "MISSING" : "OK" ) );
                    }
                }
                else
                {
                    writer.print( "  NONE" );
                }
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    private List<DependencyRelationship> filterAndSort( final Set<ProjectRelationship<?>> rels )
    {
        final Set<DependencyRelationship> filtered = new HashSet<DependencyRelationship>();

        for ( final ProjectRelationship<?> rel : rels )
        {
            if ( rel instanceof DependencyRelationship )
            {
                final DependencyRelationship dr = (DependencyRelationship) rel;
                if ( dr.isManaged() )
                {
                    filtered.add( dr );
                }
            }
        }

        final List<DependencyRelationship> sorted = new ArrayList<DependencyRelationship>( filtered );
        Collections.sort( sorted, new RelationshipComparator() );

        return sorted;
    }

}
