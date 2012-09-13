package org.commonjava.redhat.maven.rv.report;

import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "inverse-missing-build-requires.txt" )
public class InverseMissingBuildRequiresReport
    extends InverseBuildRequiresReport
{

    @Override
    protected Set<ProjectVersionRef> getReferencesToReport( final ValidatorSession session )
    {
        return session.getMissing();
    }

}
