package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.model.building.ModelProblem;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;

@Named( "models-with-failure.txt" )
public class ModelWithFailureReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            final Map<ProjectVersionRef, Set<Exception>> errors = session.getModelErrors();
            final Map<ProjectVersionRef, Set<ModelProblem>> problems = session.getModelProblems();

            final Set<ProjectVersionRef> refs = new HashSet<ProjectVersionRef>();
            refs.addAll( errors.keySet() );
            refs.addAll( problems.keySet() );

            final List<ProjectVersionRef> sortedRefs = new ArrayList<ProjectVersionRef>( refs );
            Collections.sort( sortedRefs, new ToStringComparator<ProjectVersionRef>() );

            writer.printf( join( sortedRefs, "\n" ) );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
