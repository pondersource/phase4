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
package com.helger.phase4.error;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.error.IError;
import com.helger.commons.error.SingleError;
import com.helger.commons.string.StringHelper;
import com.helger.commons.text.display.IHasDisplayText;
import com.helger.phase4.ebms3header.Ebms3Description;
import com.helger.phase4.ebms3header.Ebms3Error;
import com.helger.phase4.messaging.domain.MessageHelperMethods;

/**
 * Base interface for a single EBMS error
 *
 * @author Philip Helger
 */
public interface IEbmsError
{
  /**
   * Gets the value of the errorCode property.
   *
   * @return possible object is {@link String }
   */
  @Nonnull
  String getErrorCode ();

  /**
   * Gets the value of the severity property.
   *
   * @return possible object is {@link String }
   */
  @Nonnull
  EEbmsErrorSeverity getSeverity ();

  /**
   * Gets the value of the shortDescription property.
   *
   * @return possible object is {@link String }
   */
  @Nonnull
  String getShortDescription ();

  /**
   * Gets the value of the errorDetail property.
   *
   * @return possible object is {@link String }
   */
  @Nonnull
  IHasDisplayText getErrorDetail ();

  /**
   * Gets the value of the category property.
   *
   * @return possible object is {@link EEbmsErrorCategory }
   */
  @Nonnull
  EEbmsErrorCategory getCategory ();

  @Nonnull
  default IError getAsError (@Nonnull final Locale aContentLocale)
  {
    return SingleError.builder ()
                      .errorLevel (getSeverity ().getErrorLevel ())
                      .errorID (getErrorCode ())
                      .errorText ("[" +
                                  getCategory ().getDisplayName () +
                                  "] " +
                                  StringHelper.getNotNull (getErrorDetail ().getDisplayText (aContentLocale), getShortDescription ()))
                      .build ();
  }

  @Nonnull
  default Ebms3Error getAsEbms3Error (@Nonnull final Locale aContentLocale, @Nullable final String sRefToMessageInError)
  {
    return getAsEbms3Error (aContentLocale, sRefToMessageInError, (String) null);
  }

  @Nonnull
  default Ebms3Error getAsEbms3Error (@Nonnull final Locale aContentLocale,
                                      @Nullable final String sRefToMessageInError,
                                      @Nullable final String sErrorDescription)
  {
    return getAsEbms3Error (aContentLocale, sRefToMessageInError, (String) null, sErrorDescription);
  }

  @Nonnull
  default Ebms3Error getAsEbms3Error (@Nonnull final Locale aContentLocale,
                                      @Nullable final String sRefToMessageInError,
                                      @Nullable final String sOrigin,
                                      @Nullable final String sErrorDescription)
  {
    return getAsEbms3Error (aContentLocale,
                            sRefToMessageInError,
                            sOrigin,
                            sErrorDescription == null ? null
                                                      : MessageHelperMethods.createEbms3Description (aContentLocale, sErrorDescription));
  }

  @Nonnull
  default Ebms3Error getAsEbms3Error (@Nonnull final Locale aContentLocale,
                                      @Nullable final String sRefToMessageInError,
                                      @Nullable final String sOrigin,
                                      @Nullable final Ebms3Description aEbmsDescription)
  {
    final Ebms3Error aEbms3Error = new Ebms3Error ();
    // Default to shortDescription if none provided
    aEbms3Error.setDescription (aEbmsDescription != null ? aEbmsDescription
                                                         : MessageHelperMethods.createEbms3Description (aContentLocale,
                                                                                                        getShortDescription ()));
    aEbms3Error.setErrorDetail (getErrorDetail ().getDisplayText (aContentLocale));
    aEbms3Error.setErrorCode (getErrorCode ());
    aEbms3Error.setSeverity (getSeverity ().getSeverity ());
    aEbms3Error.setShortDescription (getShortDescription ());
    aEbms3Error.setCategory (getCategory ().getDisplayName ());
    aEbms3Error.setRefToMessageInError (sRefToMessageInError);
    aEbms3Error.setOrigin (sOrigin);
    return aEbms3Error;
  }
}
