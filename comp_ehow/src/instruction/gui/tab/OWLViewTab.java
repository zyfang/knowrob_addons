/*
 * Copyright (c) 2009-10 Daniel Nyga
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Technische Universiteit Eindhoven nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
*/
package instruction.gui.tab;

import instruction.gui.internal.PlanImporterWrapper;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class OWLViewTab extends InstructionTab {

	private static final long serialVersionUID = -7985157346179531359L;

	public static String TITLE = "OWL Recipe";

	JScrollPane scroll = null;
	JTextArea text = null;

	public OWLViewTab () {
		initialize();
	}

	public void initialize() {

		setLayout( new BorderLayout() );

		text = new JTextArea();
		text.setEditable( false );
		text.setBorder( BorderFactory.createLoweredBevelBorder() );
		text.setFont( new Font( "DialogInput", Font.PLAIN, 14 ) );
		text.setTabSize( 4 );

		addComponentListener( new ComponentListener() {

			public void componentHidden( ComponentEvent e ) {
			}

			public void componentMoved( ComponentEvent e ) {
			}

			public void componentResized( ComponentEvent e ) {
			}

			public void componentShown( ComponentEvent e ) {

				String owl = PlanImporterWrapper.getImporter().getOWLRecipe();

				if ( owl == null )
					text.setText( "No OWL recipe available" );
				else
					text.setText( owl );

				SwingUtilities.invokeLater( new Runnable() {
					public void run() {
						scroll.getVerticalScrollBar().setValue( 0 );
					}
				} );
			}

		} );

		scroll = new JScrollPane( text );
		add( scroll, BorderLayout.CENTER );
	}

}
