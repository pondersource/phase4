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
package com.helger.phase4.server.external;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.commons.collection.attr.IStringMap;
import com.helger.commons.collection.attr.StringMap;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.io.resource.ClassPathResource;
import com.helger.phase4.AS4TestConstants;
import com.helger.phase4.CAS4;
import com.helger.phase4.ScopedConfig;
import com.helger.phase4.CEF.AbstractCEFTestSetUp;
import com.helger.phase4.attachment.Phase4OutgoingAttachment;
import com.helger.phase4.attachment.WSS4JAttachment;
import com.helger.phase4.crypto.AS4SigningParams;
import com.helger.phase4.ebms3header.Ebms3CollaborationInfo;
import com.helger.phase4.ebms3header.Ebms3MessageInfo;
import com.helger.phase4.ebms3header.Ebms3MessageProperties;
import com.helger.phase4.ebms3header.Ebms3PartyInfo;
import com.helger.phase4.ebms3header.Ebms3PayloadInfo;
import com.helger.phase4.ebms3header.Ebms3Property;
import com.helger.phase4.http.AS4HttpDebug;
import com.helger.phase4.http.HttpMimeMessageEntity;
import com.helger.phase4.messaging.crypto.AS4Signer;
import com.helger.phase4.messaging.domain.AS4UserMessage;
import com.helger.phase4.messaging.domain.MessageHelperMethods;
import com.helger.phase4.messaging.mime.AS4MimeMessage;
import com.helger.phase4.messaging.mime.MimeMessageCreator;
import com.helger.phase4.server.MockJettySetup;

@Ignore ("Axis2 bug in Holodeck! Requires external proxy and PEPPOL pilot certificate!")
public final class HolodeckFuncTest extends AbstractCEFTestSetUp
{
  /** The test URL for Holodeck */
  public static final String DEFAULT_AS4_NET_URI = "http://interop.holodeck-b2b.com:8080/msh";
  private static final String COLLABORATION_INFO_SERVICE = "SRV_SIMPLE_ONEWAY_DYN";
  private static final String COLLABORATION_INFO_SERVICE_TYPE = null;
  private static final String COLLABORATION_INFO_ACTION = "ACT_SIMPLE_ONEWAY_DYN";
  private static final String TO_PARTY_ID = "holodeck-c3";
  private static ScopedConfig s_aSC;

  @BeforeClass
  public static void noJetty ()
  {
    final IStringMap aSettings = new StringMap ();
    aSettings.putIn (MockJettySetup.SETTINGS_SERVER_JETTY_ENABLED, false);
    aSettings.putIn (MockJettySetup.SETTINGS_SERVER_ADDRESS, DEFAULT_AS4_NET_URI);
    s_aSC = ScopedConfig.create (aSettings);
    AS4HttpDebug.setEnabled (false);
  }

  @AfterClass
  public static void disableDebug ()
  {
    if (s_aSC != null)
      s_aSC.close ();
    AS4HttpDebug.setEnabled (false);
  }

  @Test
  public void testSendToHolodeck () throws Exception
  {
    final ICommonsList <WSS4JAttachment> aAttachments = new CommonsArrayList <> ();
    aAttachments.add (WSS4JAttachment.createOutgoingFileAttachment (Phase4OutgoingAttachment.builder ()
                                                                                            .data (ClassPathResource.getAsFile (AS4TestConstants.TEST_SOAP_BODY_PAYLOAD_XML))
                                                                                            .mimeTypeXML ()
                                                                                            .build (),
                                                                    s_aResMgr));

    // New message ID
    final Ebms3MessageInfo aEbms3MessageInfo = MessageHelperMethods.createEbms3MessageInfo ();
    final Ebms3PayloadInfo aEbms3PayloadInfo = MessageHelperMethods.createEbms3PayloadInfo (false, aAttachments);
    final Ebms3CollaborationInfo aEbms3CollaborationInfo = MessageHelperMethods.createEbms3CollaborationInfo (true ? null
                                                                                                                   : m_aESENSOneWayPMode.getID (),
                                                                                                              true ? null
                                                                                                                   : DEFAULT_AGREEMENT,
                                                                                                              COLLABORATION_INFO_SERVICE_TYPE,
                                                                                                              COLLABORATION_INFO_SERVICE,
                                                                                                              COLLABORATION_INFO_ACTION,
                                                                                                              AS4TestConstants.TEST_CONVERSATION_ID);
    final Ebms3PartyInfo aEbms3PartyInfo = MessageHelperMethods.createEbms3PartyInfo (CAS4.DEFAULT_INITIATOR_URL,
                                                                                      "phase4-sender",
                                                                                      CAS4.DEFAULT_RESPONDER_URL,
                                                                                      TO_PARTY_ID);

    // Add properties
    final ICommonsList <Ebms3Property> aEbms3Properties = new CommonsArrayList <> (MessageHelperMethods.createEbms3Property (CAS4.ORIGINAL_SENDER,
                                                                                                                             null,
                                                                                                                             "C1"),
                                                                                   MessageHelperMethods.createEbms3Property (CAS4.FINAL_RECIPIENT,
                                                                                                                             null,
                                                                                                                             "C4"));
    aEbms3Properties.add (MessageHelperMethods.createEbms3Property ("trackingidentifier", "tracker"));
    final Ebms3MessageProperties aEbms3MessageProperties = MessageHelperMethods.createEbms3MessageProperties (aEbms3Properties);

    final AS4UserMessage aUserMsg = AS4UserMessage.create (aEbms3MessageInfo,
                                                           aEbms3PayloadInfo,
                                                           aEbms3CollaborationInfo,
                                                           aEbms3PartyInfo,
                                                           aEbms3MessageProperties,
                                                           m_eSoapVersion)
                                                  .setMustUnderstand (true);

    // Sign payload document
    final Document aSignedDoc = AS4Signer.createSignedMessage (m_aCryptoFactory,
                                                               aUserMsg.getAsSoapDocument (),
                                                               m_eSoapVersion,
                                                               aUserMsg.getMessagingID (),
                                                               aAttachments,
                                                               s_aResMgr,
                                                               false,
                                                               AS4SigningParams.createDefault ());

    final AS4MimeMessage aMsg = MimeMessageCreator.generateMimeMessage (m_eSoapVersion, aSignedDoc, aAttachments);
    sendMimeMessage (new HttpMimeMessageEntity (aMsg), true, null);
  }
}
