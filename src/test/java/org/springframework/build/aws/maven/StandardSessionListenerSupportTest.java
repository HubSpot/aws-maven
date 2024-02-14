/*
 * Copyright 2010-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.aws.maven;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.SessionEvent;
import org.apache.maven.wagon.events.SessionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public final class StandardSessionListenerSupportTest {

    private final Wagon wagon = mock(Wagon.class);

    private final SessionListener sessionListener = mock(SessionListener.class);

    private final StandardSessionListenerSupport sessionListenerSupport =
            new StandardSessionListenerSupport(this.wagon);

    @BeforeEach
    public void addSessionListener() {
        this.sessionListenerSupport.addSessionListener(this.sessionListener);
    }

    @Test
    public void sessionListenerManagement() {
        assertTrue(this.sessionListenerSupport.hasSessionListener(this.sessionListener));
        this.sessionListenerSupport.removeSessionListener(this.sessionListener);
        assertFalse(this.sessionListenerSupport.hasSessionListener(this.sessionListener));
    }

    @Test
    public void fireSessionOpening() {
        this.sessionListenerSupport.fireSessionOpening();
        verify(this.sessionListener).sessionOpening(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_OPENING));
    }

    @Test
    public void fireSessionOpened() {
        this.sessionListenerSupport.fireSessionOpened();
        verify(this.sessionListener).sessionOpened(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_OPENED));
    }

    @Test
    public void fireSessionDisconnecting() {
        this.sessionListenerSupport.fireSessionDisconnecting();
        verify(this.sessionListener).sessionDisconnecting(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_DISCONNECTING));
    }

    @Test
    public void fireSessionDisonnected() {
        this.sessionListenerSupport.fireSessionDisconnected();
        verify(this.sessionListener).sessionDisconnected(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_DISCONNECTED));
    }

    @Test
    public void fireSessionConnectionRefused() {
        this.sessionListenerSupport.fireSessionConnectionRefused();
        verify(this.sessionListener).sessionConnectionRefused(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_CONNECTION_REFUSED));
    }

    @Test
    public void fireSessionLoggedIn() {
        this.sessionListenerSupport.fireSessionLoggedIn();
        verify(this.sessionListener).sessionLoggedIn(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_LOGGED_IN));
    }

    @Test
    public void fireSessionLoggedOff() {
        this.sessionListenerSupport.fireSessionLoggedOff();
        verify(this.sessionListener).sessionLoggedOff(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getEventType() == SessionEvent.SESSION_LOGGED_OFF));
    }

    @Test
    public void fireSessionError() {
        IOException exception = new IOException();
        this.sessionListenerSupport.fireSessionError(exception);
        verify(this.sessionListener).sessionError(argThat(ev ->
                ev.getWagon().equals(this.wagon) && ev.getException().equals(exception)));
    }

}
