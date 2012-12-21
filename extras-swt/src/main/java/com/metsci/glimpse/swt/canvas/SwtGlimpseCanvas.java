/*
 * Copyright (c) 2012, Metron, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Metron, Inc. nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL METRON, INC. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.metsci.glimpse.swt.canvas;

import static com.metsci.glimpse.util.logging.LoggerUtils.*;

import java.awt.Dimension;
import java.util.List;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GLContext;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.metsci.glimpse.canvas.GlimpseCanvas;
import com.metsci.glimpse.canvas.LayoutManager;
import com.metsci.glimpse.context.GlimpseBounds;
import com.metsci.glimpse.context.GlimpseContext;
import com.metsci.glimpse.context.GlimpseContextImpl;
import com.metsci.glimpse.context.GlimpseTarget;
import com.metsci.glimpse.context.GlimpseTargetStack;
import com.metsci.glimpse.gl.GLListenerInfo;
import com.metsci.glimpse.gl.GLSimpleListener;
import com.metsci.glimpse.layout.GlimpseLayout;
import com.metsci.glimpse.support.repaint.RepaintManager;
import com.metsci.glimpse.support.settings.LookAndFeel;
import com.metsci.glimpse.swt.event.mouse.MouseWrapperSWT;
import com.metsci.glimpse.swt.misc.CursorUtil;

public class SwtGlimpseCanvas extends GLSimpleSwtCanvas implements GlimpseCanvas
{
    private static final Logger logger = Logger.getLogger( SwtGlimpseCanvas.class.getName( ) );
    
    protected Composite parent;

    protected Cursor canvasCursor;

    protected LayoutManager layoutManager;

    protected MouseWrapperSWT mouseHelper;
    protected boolean isEventConsumer = true;
    protected boolean isEventGenerator = true;
    protected boolean isDisposed = false;

    public SwtGlimpseCanvas( Composite _parent )
    {
        this( _parent, null, SWT.NO_BACKGROUND );
    }

    public SwtGlimpseCanvas( Composite _parent, GLContext _context )
    {
        this( _parent, _context, SWT.NO_BACKGROUND );
    }

    public SwtGlimpseCanvas( Composite _parent, GLContext _context, int options )
    {
        super( _parent, _context, options );

        this.parent = _parent;

        this.layoutManager = new LayoutManager( );

        this.mouseHelper = new MouseWrapperSWT( this );
        this.addMouseListener( this.mouseHelper );
        this.addMouseMoveListener( this.mouseHelper );
        this.addMouseWheelListener( this.mouseHelper );
        this.addMouseTrackListener( this.mouseHelper );

        this.addFocusListener( this );

        this.setPlotAreaCursor( CursorUtil.crosshairs( _parent.getDisplay( ) ) );

        this.addListener( new GLSimpleListener( )
        {
            @Override
            public void init( GLContext context )
            {
                try
                {
                    GL gl = context.getGL( );
                    gl.setSwapInterval( 0 );
                }
                catch ( Exception e )
                {
                    // without this, repaint rate is tied to screen refresh rate on some systems
                    // this doesn't work on some machines (Mac OSX in particular)
                    // but it's not a big deal if it fails
                    logWarning( logger, "Trouble in init.", e );
                }
            }

            @Override
            public void display( GLContext context )
            {
                for ( GlimpseLayout layout : layoutManager.getLayoutList( ) )
                {
                    layout.paintTo( getGlimpseContext( ) );
                }
            }

            @Override
            public void reshape( GLContext context, int x, int y, int width, int height )
            {
                for ( GlimpseLayout layout : layoutManager.getLayoutList( ) )
                {
                    layout.layoutTo( getGlimpseContext( ) );
                }
            }

            @Override
            public void displayChanged( GLContext context, boolean modeChanged, boolean deviceChanged )
            {
                // do nothing
            }

            @Override
            public void dispose( GLContext context )
            {
                // do nothing
            }

            @Override
            public boolean isDisposed( )
            {
                // return dummy value
                return false;
            }

            @Override
            public GLListenerInfo getInfo( )
            {
                // return dummy value
                return null;
            }
        } );
    }

    @Override
    public GlimpseContext getGlimpseContext( )
    {
        return new GlimpseContextImpl( this );
    }

    @Override
    public void setLookAndFeel( LookAndFeel laf )
    {
        for ( GlimpseTarget target : this.layoutManager.getLayoutList( ) )
        {
            target.setLookAndFeel( laf );
        }
    }

    @Override
    public void addLayout( GlimpseLayout layout )
    {
        this.layoutManager.addLayout( layout );
    }

    @Override
    public void addLayout( GlimpseLayout layout, int zOrder )
    {
        this.layoutManager.addLayout( layout, zOrder );
    }

    @Override
    public void setZOrder( GlimpseLayout layout, int zOrder )
    {
        this.layoutManager.setZOrder( layout, zOrder );
    }

    @Override
    public void removeLayout( GlimpseLayout layout )
    {
        this.layoutManager.removeLayout( layout );
    }
    
    @Override
    public void removeAllLayouts( )
    {
        this.layoutManager.removeAllLayouts( );
    }

    @SuppressWarnings( { "unchecked", "rawtypes" } )
    @Override
    public List<GlimpseTarget> getTargetChildren( )
    {
        // layoutManager returns an unmodifiable list, thus this cast is typesafe
        // (there is no way for the recipient of the List<GlimpseTarget> view to
        // add GlimpseTargets which are not GlimpseLayouts to the list)
        return ( List ) this.layoutManager.getLayoutList( );
    }

    public Dimension getDimension( )
    {
        if ( !isDisposed( ) )
        {
            Rectangle rect = getClientArea( );
            return new Dimension( rect.width, rect.height );
        }
        else
        {
            return null;
        }
    }

    @Override
    public GlimpseBounds getTargetBounds( GlimpseTargetStack stack )
    {
        Dimension dimension = getDimension( );

        if ( dimension != null )
        {
            return new GlimpseBounds( getDimension( ) );
        }
        else
        {
            return null;
        }
    }

    @Override
    public GlimpseBounds getTargetBounds( )
    {
        return getTargetBounds( null );
    }

    @Override
    public void paint( )
    {
        if ( !parent.isDisposed( ) )
        {
            parent.getDisplay( ).syncExec( new Runnable( )
            {
                @Override
                public void run( )
                {
                    if ( !parent.isDisposed( ) )
                    {
                        draw( );
                    }
                }
            } );
        }
    }

    public void setPlotAreaCursor( Cursor cursor )
    {
        canvasCursor = cursor;
        setCursor( canvasCursor );
    }

    // In linux, the component the mouse pointer is over receives mouse wheel
    // events
    // In windows, the component with focus receives mouse wheel events
    // These listeners emulate linux-like mouse wheel event dispatch for
    // important components
    // This causes the application to work in slightly un-windows-like ways
    // some of the time, but the effect is minor.
    protected void addFocusListener( final Control control )
    {
        control.addMouseTrackListener( new MouseTrackListener( )
        {
            @Override
            public void mouseEnter( MouseEvent e )
            {
                control.setFocus( );
            }

            @Override
            public void mouseExit( MouseEvent e )
            {
            }

            @Override
            public void mouseHover( MouseEvent e )
            {
            }
        } );
    }

    @Override
    public String toString( )
    {
        return SwtGlimpseCanvas.class.getSimpleName( );
    }

    @Override
    public boolean isEventConsumer( )
    {
        return this.isEventConsumer;
    }

    @Override
    public void setEventConsumer( boolean consume )
    {
        this.isEventConsumer = consume;
    }

    @Override
    public boolean isEventGenerator( )
    {
        return this.isEventGenerator;
    }

    @Override
    public void setEventGenerator( boolean generate )
    {
        this.isEventGenerator = generate;
    }
    
    @Override
    public boolean isDisposed( )
    {
        return isDisposed;
    }
    
    @Override
    public void dispose( )
    {
        super.dispose( );
        canvasCursor.dispose( );
    }
    
    @Override
    public void dispose( RepaintManager manager )
    {
        Runnable dispose = new Runnable( )
        {
            @Override
            public void run( )
            {
                GLContext glContext = getGLContext( );
                GlimpseContext context = new GlimpseContextImpl( glContext );
                glContext.makeCurrent( );
                try
                {
                    for ( GlimpseLayout layout : layoutManager.getLayoutList( ) )
                    {
                        layout.dispose( context );
                    }
                }
                finally
                {
                    glContext.release( );
                }
            }
        };
        
        if ( manager != null )
        {
            manager.syncExec( dispose );   
        }
        else
        {
            dispose.run( );
        }
        
        dispose( );
    
        isDisposed = true;
    }
}
