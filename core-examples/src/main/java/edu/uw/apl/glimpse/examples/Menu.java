/*
  Copyright (c) 2015, University of Washington.
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of University of Washington nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE UNIVERSITY
OF WASHINGTON BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.  
*/

package edu.uw.apl.glimpse.examples;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/**
 * @author Stuart Maclean
 *
 * A helper class for all the runnable Glimpse Example classes in the 
 * core-examples Maven module.
 *
 * Locate all classes on the classpath (well, those in the same jar as
 * this class) whose package name starts com.metsci.glimpse.examples.
 * Check such a class has a method 'static void main( String[] )'.  If yes,
 * add to a list of 'invokable classes'.
 *
 * Once the invokable class list is known, present a simple
 * console-based menu, showing all the invokable class names.  Read an
 * index from the user, locate the invokable class in the list given
 * the index, and invoke.
 *
 * To avoid the menu and just invoke example N explicitly, can pass N
 * in args[0].
 */

public class Menu {

    static public void main( String[] args ) {

	String prefix = "com/metsci/glimpse/examples/";

	Enumeration<URL> us = null;
	try {
	    us = Menu.class.getClassLoader().getResources( prefix );
	} catch( Exception e ) {
	    System.err.println( e );
	    return;
	}
	
	if( !us.hasMoreElements() )
	    return;
	URL u = us.nextElement();
	String s = u.getPath();

	// LOOK: is the url format consistent across all platforms?
	s = s.substring( s.indexOf( ":" ) + 1, s.indexOf( "!" ) );
	
	File f = new File( s );
	if( !f.exists() )
	    return;
	
	List<String> classNames = new ArrayList<String>();

	ZipFile zf = null;
	try {
	    zf = new ZipFile( f );
	} catch( Exception e ) {
	    System.err.println( e );
	    return;
	}

	// Walk the zip contents, converting resource names to class names...
	Enumeration<? extends ZipEntry> zes = zf.entries();
	while( zes.hasMoreElements() ) {
	    ZipEntry ze = zes.nextElement();
	    String name = ze.getName();
	    if( !name.startsWith( prefix ) )
		continue;
	    if( !name.endsWith( ".class" ) )
		continue;
	    name = name.substring( 0, name.length() - ".class".length() );
	    name = name.replaceAll( "/", "." );
	    classNames.add( name );
	}
	
	// Sort for the sake of the ensuing menu offered to the user...
	Collections.sort( classNames );
	
	/*
	  Attempt to load each class and see if it has a main method
	  of the suitable signature...
	*/
	List<String> classesWithMain = new ArrayList<String>();
	List<Method> methods = new ArrayList<Method>();
	for( String name : classNames ) {
	    Class<?> c = null;
	    try {
		c = Class.forName( name );
	    } catch( Exception e ) {
		System.err.println( e );
		continue;
	    }
	    try {
		Method m = c.getDeclaredMethod
		    ( "main", new Class[] { String[].class } );
		int mod = m.getModifiers();
		if( !( Modifier.isPublic( mod ) && Modifier.isStatic( mod ) ) )
		    continue;
		methods.add( m );
		classesWithMain.add( name );
	    } catch( NoSuchMethodException nsme ) {
		continue;
	    }
	}
	
	if( args.length > 0 ) {
	    String arg = args[0];
	    int choice = -1;
	    try {
		choice = Integer.parseInt( arg );
	    } catch( NumberFormatException nfe ) {
		return;
	    }
	    if( choice < 1 || choice > classesWithMain.size() )
		return;
	    Method m = methods.get( choice-1 );
	    try {
		m.invoke( null, new Object[] { new String[0] } );
	    } catch( Exception e ) {
		System.err.println( e );
	    }
	    return;
	}

	Console console = System.console();
	if( console == null )
	    return;

	StringWriter sw = new StringWriter();
	PrintWriter pw = new PrintWriter( sw );
	int i = 1;
	for( String name : classesWithMain ) {
	    pw.println( i + " - " + name );
	    i++;
	}
	String menu = sw.toString();
	
	
	// We'd like to keep trying ALL examples, but see below...
	while( true ) {
	    System.out.print( menu );
	    System.out.print( "Run Example: " );
	    System.out.flush();
	    String line = console.readLine();
	    if( line == null )
		break;
	    line = line.trim();
	    int choice = -1;
	    try {
		choice = Integer.parseInt( line );
	    } catch( NumberFormatException nfe ) {
		continue;
	    }
	    if( choice < 1 || choice > classesWithMain.size() )
		continue;

	    Method m = methods.get(choice-1);

	    /* 
	       Currently, if/when the invoked method calls
	       System.exit(), we will exit too.  This is OK, but would
	       be nice to catch this and return here to the menu, to
	       select another example to run.  Perhaps install a
	       SecurityManager which disables (prohibits?)
	       System.exit() ??
	    */

	    try {
		m.invoke( null, new Object[] { new String[0] } );
	    } catch( Exception e ) {
		System.err.println( e );
	    }
	}
    }
}

// eof
