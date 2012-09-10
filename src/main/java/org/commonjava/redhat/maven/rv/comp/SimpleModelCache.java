package org.commonjava.redhat.maven.rv.comp;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.model.building.ModelCache;

public class SimpleModelCache
    implements ModelCache
{

    private Map<Key, Object> cache = new HashMap<Key, Object>();

    public void put( final String groupId, final String artifactId, final String version, final String tag,
                     final Object data )
    {
        cache.put( new Key( groupId, artifactId, version, tag ), data );
    }

    public Object get( final String groupId, final String artifactId, final String version, final String tag )
    {
        return cache.get( new Key( groupId, artifactId, version, tag ) );
    }

    private static final class Key
    {
        private String groupId, artifactId, version, tag;

        Key( final String groupId, final String artifactId, final String version, final String tag )
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.tag = tag;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ( ( artifactId == null ) ? 0 : artifactId.hashCode() );
            result = prime * result + ( ( groupId == null ) ? 0 : groupId.hashCode() );
            result = prime * result + ( ( tag == null ) ? 0 : tag.hashCode() );
            result = prime * result + ( ( version == null ) ? 0 : version.hashCode() );
            return result;
        }

        @Override
        public boolean equals( final Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null )
            {
                return false;
            }
            if ( getClass() != obj.getClass() )
            {
                return false;
            }
            final Key other = (Key) obj;
            if ( artifactId == null )
            {
                if ( other.artifactId != null )
                {
                    return false;
                }
            }
            else if ( !artifactId.equals( other.artifactId ) )
            {
                return false;
            }
            if ( groupId == null )
            {
                if ( other.groupId != null )
                {
                    return false;
                }
            }
            else if ( !groupId.equals( other.groupId ) )
            {
                return false;
            }
            if ( tag == null )
            {
                if ( other.tag != null )
                {
                    return false;
                }
            }
            else if ( !tag.equals( other.tag ) )
            {
                return false;
            }
            if ( version == null )
            {
                if ( other.version != null )
                {
                    return false;
                }
            }
            else if ( !version.equals( other.version ) )
            {
                return false;
            }
            return true;
        }
    }

}
