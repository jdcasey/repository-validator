package org.commonjava.redhat.maven.rv;

import static org.commonjava.redhat.maven.rv.VersionInfo.APP_BUILDER;
import static org.commonjava.redhat.maven.rv.VersionInfo.APP_COMMIT_ID;
import static org.commonjava.redhat.maven.rv.VersionInfo.APP_DESCRIPTION;
import static org.commonjava.redhat.maven.rv.VersionInfo.APP_NAME;
import static org.commonjava.redhat.maven.rv.VersionInfo.APP_TIMESTAMP;
import static org.commonjava.redhat.maven.rv.VersionInfo.APP_VERSION;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.commonjava.redhat.maven.rv.mgr.ValidationManager;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.jboss.weld.environment.se.Weld;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class CLI
{
    private static final String USER_DIR = System.getProperty( "user.dir", "." );

    @Argument( index = 0, metaVar = "repository", usage = "Directory containing repository to validate." )
    private File repository = new File( USER_DIR );

    @Option( name = "-e", usage = "POM exclude path pattern (glob)" )
    private String pomExcludePattern;

    @Option( name = "-h", aliases = { "--help" }, usage = "Print this message and quit" )
    private boolean help = false;

    @Option( name = "-W", aliases = { "--workspace" }, usage = "Location where output should be written, temp files cached, etc.\nDefault: rv-workspace" )
    private File workspace = new File( USER_DIR, "rv-workspace" );

    @Option( name = "-R", aliases = { "--reports" }, usage = "Write reports here.\nDefault: rv-workspace/reports" )
    private File reports;

    @Option( name = "-Z", aliases = { "--no-system-exit" }, usage = "Don't call System.exit(..) (for embedding/testing)." )
    private boolean noSystemExit;

    @Option( name = "-v", aliases = { "-version", "--version" }, usage = "Print the version and quit." )
    private boolean showVersion;

    @Option( name = "-s", aliases = { "--settings" }, usage = "Settings.xml used to specify server authentications for use in artifact resolution" )
    private String settingsXml;

    @Option( name = "-r", aliases = { "--remote-repository" }, usage = "Remote repository URL to use in resolving dependencies, plugins, etc. (specify more than once to use multiple remotes)", multiValued = true )
    private List<String> remoteRepositories;

    public static void main( final String[] args )
    {
        final CLI cli = new CLI();
        final CmdLineParser parser = new CmdLineParser( cli );

        int exitValue = 0;
        try
        {
            parser.parseArgument( args );

            if ( cli.help )
            {
                printUsage( parser, null );
            }
            else if ( cli.showVersion )
            {
                System.out.printf( "\n\n%s, version: %s\n\n%s\n\nBuilt by: %s\nOn: %s\nCommit ID: %s\n\n\n", APP_NAME,
                                   APP_VERSION, APP_DESCRIPTION, APP_BUILDER, APP_TIMESTAMP, APP_COMMIT_ID );
            }
            else
            {
                cli.run();
            }
        }
        catch ( final CmdLineException e )
        {
            e.printStackTrace();
            exitValue = -1;
        }
        catch ( final ValidationException e )
        {
            e.printStackTrace();
            exitValue = -2;
        }

        if ( !cli.noSystemExit )
        {
            System.exit( exitValue );
        }
    }

    public void run()
        throws ValidationException
    {
        final long start = System.currentTimeMillis();
        try
        {
            final ValidatorSession session =
                new ValidatorSession.Builder( repository, workspace ).withReportsDirectory( reports )
                                                                     .withPomExcludes( pomExcludePattern )
                                                                     .withSettingsXmlPath( settingsXml )
                                                                     .withRemoteRepositoryUrls( remoteRepositories )
                                                                     .build();
            new Weld().initialize()
                      .instance()
                      .select( ValidationManager.class )
                      .get()
                      .validate( session );
        }
        finally
        {
            try
            {
                final long elapsed = System.currentTimeMillis() - start;

                final long hr = TimeUnit.MILLISECONDS.toHours( elapsed );

                final long min = TimeUnit.MILLISECONDS.toMinutes( elapsed - TimeUnit.HOURS.toMillis( hr ) );

                final long sec =
                    TimeUnit.MILLISECONDS.toSeconds( elapsed - TimeUnit.HOURS.toMillis( hr )
                        - TimeUnit.MINUTES.toMillis( min ) );

                final long ms =
                    TimeUnit.MILLISECONDS.toMillis( elapsed - TimeUnit.HOURS.toMillis( hr )
                        - TimeUnit.MINUTES.toMillis( min ) - TimeUnit.SECONDS.toMillis( sec ) );

                final String elapsedInterval = String.format( "%02d:%02d:%02d.%03d", hr, min, sec, ms );

                System.out.printf( "\n\nElapsed time for validation attempt: %s\n\n", elapsedInterval );
            }
            catch ( final Error e )
            {
                e.printStackTrace();
            }
        }
    }

    private static void printUsage( final CmdLineParser parser, final Exception error )
    {
        if ( error != null )
        {
            System.err.println( "Invalid option(s): " + error.getMessage() );
            System.err.println();
        }

        System.err.println( "Usage: $0 [OPTIONS] [<target-path>]" );
        System.err.println();
        System.err.println();
        // If we are running under a Linux shell COLUMNS might be available for the width
        // of the terminal.
        parser.setUsageWidth( ( System.getenv( "COLUMNS" ) == null ? 100 : Integer.valueOf( System.getenv( "COLUMNS" ) ) ) );
        parser.printUsage( System.err );
        System.err.println();
    }
}
