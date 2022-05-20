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
package com.helger.phase4.messaging.domain;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.string.ToStringGenerator;
import com.helger.commons.traits.IGenericImplTrait;
import com.helger.phase4.CAS4;
import com.helger.phase4.ebms3header.Ebms3Messaging;
import com.helger.phase4.marshaller.Ebms3WriterBuilder;
import com.helger.phase4.soap.ESoapVersion;
import com.helger.phase4.soap11.Soap11Body;
import com.helger.phase4.soap11.Soap11Envelope;
import com.helger.phase4.soap11.Soap11Header;
import com.helger.phase4.soap12.Soap12Body;
import com.helger.phase4.soap12.Soap12Envelope;
import com.helger.phase4.soap12.Soap12Header;

/**
 * Abstract AS4 message implementation
 *
 * @author Philip Helger
 * @param <IMPLTYPE>
 *        Real implementation type.
 */
public abstract class AbstractAS4Message <IMPLTYPE extends AbstractAS4Message <IMPLTYPE>> implements
                                         IAS4Message,
                                         IGenericImplTrait <IMPLTYPE>
{
  private final ESoapVersion m_eSoapVersion;
  private final EAS4MessageType m_eMsgType;
  private final String m_sMessagingID;
  protected final Ebms3Messaging m_aMessaging = new Ebms3Messaging ();

  public AbstractAS4Message (@Nonnull final ESoapVersion eSoapVersion, @Nonnull final EAS4MessageType eMsgType)
  {
    m_eSoapVersion = ValueEnforcer.notNull (eSoapVersion, "SoapVersion");
    m_eMsgType = ValueEnforcer.notNull (eMsgType, "MessageType");
    m_sMessagingID = MessageHelperMethods.createRandomMessagingID ();

    // Must be a "wsu:Id" for WSSec to be found
    m_aMessaging.getOtherAttributes ().put (new QName (CAS4.WSU_NS, "Id"), m_sMessagingID);
  }

  @Nonnull
  public final ESoapVersion getSoapVersion ()
  {
    return m_eSoapVersion;
  }

  @Nonnull
  public final EAS4MessageType getMessageType ()
  {
    return m_eMsgType;
  }

  @Nonnull
  @Nonempty
  public final String getMessagingID ()
  {
    return m_sMessagingID;
  }

  @Nonnull
  public final IMPLTYPE setMustUnderstand (final boolean bMustUnderstand)
  {
    switch (m_eSoapVersion)
    {
      case SOAP_11:
        m_aMessaging.setS11MustUnderstand (Boolean.valueOf (bMustUnderstand));
        break;
      case SOAP_12:
        m_aMessaging.setS12MustUnderstand (Boolean.valueOf (bMustUnderstand));
        break;
      default:
        throw new IllegalStateException ("Unsupported SOAP version " + m_eSoapVersion);
    }
    return thisAsT ();
  }

  @Nonnull
  public final Document getAsSoapDocument (@Nullable final Node aPayload)
  {
    // Convert to DOM Node
    final Document aEbms3Document = Ebms3WriterBuilder.ebms3Messaging ().getAsDocument (m_aMessaging);
    if (aEbms3Document == null)
      throw new IllegalStateException ("Failed to write EBMS3 Messaging to XML");

    final Node aRealPayload = aPayload instanceof Document ? ((Document) aPayload).getDocumentElement () : aPayload;

    switch (m_eSoapVersion)
    {
      case SOAP_11:
      {
        // Creating SOAP 11 Envelope
        final Soap11Envelope aSoapEnv = new Soap11Envelope ();
        aSoapEnv.setHeader (new Soap11Header ());
        aSoapEnv.setBody (new Soap11Body ());
        aSoapEnv.getHeader ().addAny (aEbms3Document.getDocumentElement ());
        if (aRealPayload != null)
          aSoapEnv.getBody ().addAny (aRealPayload);
        return Ebms3WriterBuilder.soap11 ().getAsDocument (aSoapEnv);
      }
      case SOAP_12:
      {
        // Creating SOAP 12 Envelope
        final Soap12Envelope aSoapEnv = new Soap12Envelope ();
        aSoapEnv.setHeader (new Soap12Header ());
        aSoapEnv.setBody (new Soap12Body ());
        aSoapEnv.getHeader ().addAny (aEbms3Document.getDocumentElement ());
        if (aRealPayload != null)
          aSoapEnv.getBody ().addAny (aRealPayload);
        return Ebms3WriterBuilder.soap12 ().getAsDocument (aSoapEnv);
      }
      default:
        throw new IllegalStateException ("Unsupported SOAP version " + m_eSoapVersion);
    }
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("SOAPVersion", m_eSoapVersion)
                                       .append ("MsgType", m_eMsgType)
                                       .append ("MessagingID", m_sMessagingID)
                                       .getToString ();
  }
}
