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
package com.helger.phase4.messaging.mime;

import java.io.InputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import com.helger.commons.string.ToStringGenerator;

/**
 * Special wrapper around a {@link MimeMessage} with an indicator if the message
 * can be written more than once.
 * 
 * @author Philip Helger
 */
public class AS4MimeMessage extends MimeMessage
{
  private final boolean m_bIsRepeatable;

  public AS4MimeMessage (@Nullable final Session aSession, final boolean bIsRepeatable)
  {
    super (aSession);
    m_bIsRepeatable = bIsRepeatable;
  }

  public AS4MimeMessage (@Nullable final Session aSession, @Nonnull final InputStream aIS) throws MessagingException
  {
    super (aSession, aIS);
    m_bIsRepeatable = false;
  }

  public final boolean isRepeatable ()
  {
    return m_bIsRepeatable;
  }

  @Override
  public String toString ()
  {
    return ToStringGenerator.getDerived (super.toString ()).append ("IsRepeatable", m_bIsRepeatable).getToString ();
  }
}
