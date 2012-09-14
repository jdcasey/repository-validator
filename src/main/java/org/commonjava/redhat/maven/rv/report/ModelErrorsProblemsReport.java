package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.model.building.ModelProblem;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "missing-errors-problems.log" )
public class ModelErrorsProblemsReport
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

            for ( final ProjectVersionRef ref : refs )
            {
                final Set<Exception> modelErrors = errors.get( ref );
                final Set<ModelProblem> modelProblems = problems.get( ref );

                if ( isEmpty( modelErrors ) && isEmpty( modelProblems ) )
                {
                    continue;
                }

                writer.printf( "\n%s:\n----------------------\n\nProblems:", ref );
                if ( modelProblems != null )
                {
                    int idx = 0;
                    for ( final ModelProblem prob : modelProblems )
                    {
                        writer.printf( "\n  %d:  %s", idx, prob );
                        idx++;
                    }
                }
                else
                {
                    writer.print( "\n\n  NONE" );
                }

                writer.printf( "\n\nErrors:\n  ", ref );
                if ( modelErrors != null )
                {
                    int idx = 0;
                    for ( final Exception exception : modelErrors )
                    {
                        writer.printf( "\n\n  %d: ", idx );
                        exception.printStackTrace( writer );
                        idx++;
                    }
                }

                writer.println();
                writer.println();
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    private boolean isEmpty( final Collection<?> collection )
    {
        return collection == null || collection.isEmpty();
    }

}
