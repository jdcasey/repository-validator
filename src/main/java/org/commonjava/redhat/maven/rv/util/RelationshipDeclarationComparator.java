package org.commonjava.redhat.maven.rv.util;

import java.util.Comparator;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.rel.ProjectRelationship;

public class RelationshipDeclarationComparator
    implements Comparator<ProjectRelationship<?>>
{

    private ToStringComparator<ProjectVersionRef> refComp = new ToStringComparator<ProjectVersionRef>();

    public int compare( final ProjectRelationship<?> one, final ProjectRelationship<?> two )
    {
        if ( one.getType() == two.getType() )
        {
            return refComp.compare( one.getDeclaring(), two.getDeclaring() );
        }
        else
        {
            return one.getType()
                      .ordinal() - two.getType()
                                      .ordinal();
        }
    }

}
