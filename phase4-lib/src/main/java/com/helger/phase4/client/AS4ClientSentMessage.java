/*
 * Copyright (C) 2015-2022 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.client;

import java.time.OffsetDateTime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.http.StatusLine;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.http.HttpHeaderMap;
import com.helger.commons.string.ToStringGenerator;
import com.helger.phase4.mgr.MetaAS4Manager;

/**
 * This class correlates the built source message
 * ({@link AS4ClientBuiltMessage}) with the HTTP response of the passed type
 * (<code>T</code>).
 *
 * @author Philip Helger
 * @param <T>
 *        The response type
 */
@Immutable
public class AS4ClientSentMessage <T>
{
  private final AS4ClientBuiltMessage m_aBuiltMsg;
  private final StatusLine m_aResponseStatusLine;
  private HttpHeaderMap m_aResponseHeaders;
  private final T m_aResponseContent;
  private final OffsetDateTime m_aSentDateTime;

  /**
   * @param aBuiltMsg
   *        The built message with headers, payload and message ID. May not be
   *        <code>null</code>.
   * @param aResponseStatusLine
   *        The HTTP response status line. May be <code>null</code>.
   * @param aResponseHeaders
   *        The HTTP response header. May not be <code>null</code>.
   * @param aResponseContent
   *        The response payload. May be <code>null</code>.
   */
  public AS4ClientSentMessage (@Nonnull final AS4ClientBuiltMessage aBuiltMsg,
                               @Nullable final StatusLine aResponseStatusLine,
                               @Nonnull final HttpHeaderMap aResponseHeaders,
                               @Nullable final T aResponseContent)
  {
    this (aBuiltMsg, aResponseStatusLine, aResponseHeaders, aResponseContent, MetaAS4Manager.getTimestampMgr ().getCurrentDateTime ());
  }

  /**
   * @param aBuiltMsg
   *        The built message with headers, payload and message ID. May not be
   *        <code>null</code>.
   * @param aResponseStatusLine
   *        The HTTP response status line. May be <code>null</code>.
   * @param aResponseHeaders
   *        The HTTP response header. May not be <code>null</code>.
   * @param aResponseContent
   *        The response payload. May be <code>null</code>.
   * @param aSentDateTime
   *        The sending date time. May not be <code>null</code>.
   */
  protected AS4ClientSentMessage (@Nonnull final AS4ClientBuiltMessage aBuiltMsg,
                                  @Nullable final StatusLine aResponseStatusLine,
                                  @Nonnull final HttpHeaderMap aResponseHeaders,
                                  @Nullable final T aResponseContent,
                                  @Nonnull final OffsetDateTime aSentDateTime)
  {
    ValueEnforcer.notNull (aBuiltMsg, "BuiltMsg");
    ValueEnforcer.notNull (aResponseHeaders, "ResponseHeaders");
    ValueEnforcer.notNull (aSentDateTime, "SentDateTime");
    m_aBuiltMsg = aBuiltMsg;
    m_aResponseStatusLine = aResponseStatusLine;
    m_aResponseHeaders = aResponseHeaders;
    m_aResponseContent = aResponseContent;
    m_aSentDateTime = aSentDateTime;
  }

  /**
   * @return The built message as provided in the constructor. Never
   *         <code>null</code>.
   */
  @Nonnull
  public final AS4ClientBuiltMessage getBuiltMessage ()
  {
    return m_aBuiltMsg;
  }

  /**
   * @return The message ID of the sent out message. Neither <code>null</code>
   *         nor empty. This is a shortcut for
   *         <code>getBuiltMessage().getMessageID ()</code>.
   */
  @Nonnull
  @Nonempty
  public final String getMessageID ()
  {
    return m_aBuiltMsg.getMessageID ();
  }

  /**
   * @return The HTTP response status line. It contains the HTTP version, the
   *         response code and the response reason (if present). May be
   *         <code>null</code>.
   * @since 0.13.0
   */
  @Nullable
  public final StatusLine getResponseStatusLine ()
  {
    return m_aResponseStatusLine;
  }

  /**
   * @return <code>true</code> if a response status line is present,
   *         <code>false</code> if not.
   * @since 0.13.0
   */
  public final boolean hasResponseStatusLine ()
  {
    return m_aResponseStatusLine != null;
  }

  /**
   * @return The HTTP response headers as a mutable map. Never
   *         <code>null</code>.
   * @since 0.13.0
   */
  @Nonnull
  public final HttpHeaderMap getResponseHeaders ()
  {
    return m_aResponseHeaders;
  }

  /**
   * @return The response payload. May be <code>null</code>.
   */
  @Nullable
  public final T getResponse ()
  {
    return m_aResponseContent;
  }

  /**
   * @return <code>true</code> if a response payload is present,
   *         <code>false</code> if not.
   */
  public final boolean hasResponse ()
  {
    return m_aResponseContent != null;
  }

  /**
   * @return The sent date time. Never <code>null</code>.
   * @since 0.10.0
   */
  @Nonnull
  public final OffsetDateTime getSentDateTime ()
  {
    return m_aSentDateTime;
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("BuiltMsg", m_aBuiltMsg)
                                       .append ("ResponseStatusLine", m_aResponseStatusLine)
                                       .append ("Response", m_aResponseContent)
                                       .append ("SentDateTime", m_aSentDateTime)
                                       .getToString ();
  }
}
