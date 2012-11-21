/*
 *  Copyright (C) 2012 Red Hat, Inc.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.commonjava.redhat.maven.rv.util;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.log4j.Logger;
import org.commonjava.redhat.maven.rv.ValidationException;

public final class InputUtils
{

    private static final Logger LOGGER = Logger.getLogger( InputUtils.class );

    private InputUtils()
    {
    }

    private static DefaultHttpClient client;

    public static File getFile( final String location, final File downloadsDir )
        throws ValidationException
    {
        return getFile( location, downloadsDir, false );
    }

    public static File getFile( final String location, final File downloadsDir, final boolean deleteExisting )
        throws ValidationException
    {
        if ( client == null )
        {
            final DefaultHttpClient hc = new DefaultHttpClient();
            hc.setRedirectStrategy( new DefaultRedirectStrategy() );

            final String proxyHost = System.getProperty( "http.proxyHost" );
            final int proxyPort = Integer.parseInt( System.getProperty( "http.proxyPort", "-1" ) );

            if ( proxyHost != null && proxyPort > 0 )
            {
                final HttpHost proxy = new HttpHost( proxyHost, proxyPort );
                hc.getParams()
                  .setParameter( ConnRouteParams.DEFAULT_PROXY, proxy );
            }

            client = hc;
        }

        File result = null;

        if ( location.startsWith( "http" ) )
        {
            LOGGER.info( "Downloading: '" + location + "'..." );

            try
            {
                final URL url = new URL( location );
                final String userpass = url.getUserInfo();
                if ( !isEmpty( userpass ) )
                {
                    final AuthScope scope = new AuthScope( url.getHost(), url.getPort() );
                    final Credentials creds = new UsernamePasswordCredentials( userpass );

                    client.getCredentialsProvider()
                          .setCredentials( scope, creds );
                }
            }
            catch ( final MalformedURLException e )
            {
                LOGGER.error( "Malformed URL: '" + location + "'", e );
                throw new ValidationException( "Failed to download: %s. Reason: %s", e, location, e.getMessage() );
            }

            final File downloaded = new File( downloadsDir, new File( location ).getName() );
            if ( deleteExisting && downloaded.exists() )
            {
                downloaded.delete();
            }

            if ( !downloaded.exists() )
            {
                HttpGet get = new HttpGet( location );
                OutputStream out = null;
                try
                {
                    HttpResponse response = client.execute( get );
                    // Work around for scenario where we are loading from a server
                    // that does a refresh e.g. gitweb
                    if ( response.containsHeader( "Cache-control" ) )
                    {
                        LOGGER.info( "Waiting for server to generate cache..." );
                        try
                        {
                            Thread.sleep( 5000 );
                        }
                        catch ( final InterruptedException e )
                        {
                        }
                        get.abort();
                        get = new HttpGet( location );
                        response = client.execute( get );
                    }
                    final int code = response.getStatusLine()
                                             .getStatusCode();
                    if ( code == 200 )
                    {
                        final InputStream in = response.getEntity()
                                                       .getContent();
                        out = new FileOutputStream( downloaded );

                        copy( in, out );
                    }
                    else
                    {
                        LOGGER.info( String.format( "Received status: '%s' while downloading: %s",
                                                    response.getStatusLine(), location ) );

                        throw new ValidationException( "Received status: '%s' while downloading: %s",
                                                       response.getStatusLine(), location );
                    }
                }
                catch ( final ClientProtocolException e )
                {
                    throw new ValidationException( "Failed to download: '%s'. Error: %s", e, location, e.getMessage() );
                }
                catch ( final IOException e )
                {
                    throw new ValidationException( "Failed to download: '%s'. Error: %s", e, location, e.getMessage() );
                }
                finally
                {
                    closeQuietly( out );
                    get.abort();
                }
            }

            result = downloaded;
        }
        else
        {
            LOGGER.info( "Using local file: '" + location + "'..." );

            result = new File( location );
        }

        return result;
    }

}
