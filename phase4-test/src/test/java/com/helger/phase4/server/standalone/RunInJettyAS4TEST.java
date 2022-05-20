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
package com.helger.phase4.server.standalone;

import com.helger.photon.jetty.JettyStarter;

/**
 * Run this AS4 server locally using Jetty on port 8080 in / context.
 *
 * @author Philip Helger
 */
public final class RunInJettyAS4TEST
{

  public static void main (final String... args) throws Exception
  {
    new JettyStarter (RunInJettyAS4TEST.class).run ();
  }
}
