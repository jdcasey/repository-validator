package org.commonjava.redhat.maven.rv.util;

import java.util.Comparator;

public class ToStringComparator<T>
    implements Comparator<T>
{

    public int compare( final T first, final T second )
    {
        // this is completely stupid, but we don't have much recourse.
        if ( first == null && second != null )
        {
            return 1;
        }
        else if ( first != null && second == null )
        {
            return -1;
        }

        return first.toString()
                    .compareTo( second.toString() );
    }

}
