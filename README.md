# Wowza Caption Handlers
The **SCTE35 add insertion** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) can be used to intercept SCTE-35 splice events in an incoming MPEG-TS stream for the purpose of creating an HLS playlist or MPEG-DASH manifest with Server-Side Ad Insertion (SSAI) tags. SSAI providers, such as AWS Elemental MediaTailor, can detect this ad-signaling metadata to stitch ads into a live stream on a per-user basis without requiring player-side logic for ad decisions.

## Prerequisites
* Wowza Streaming Engine™ 4.8.26 or later is required.
* Java 11.

## UsageThe easiest way to set up this module is to include the repository JAR file in the lib folder of your Wowza Streaming Engine installation directory ([install-dir]/lib).
Next, configure the module and set the appropriate properties on the Wowza Streaming Engine Manager, depending on the solution you are implementing.
Refer to the guide in the [Resources](#resources) section for detailed instructions on how to install and configure the module.

## Build instructions
* Clone repo to local filesystem.
* Run `gradle build` to build the jar file.

## Resources
For full install instructions and to use the compiled version of these modules, see the following article:
* [Insert ad markers for SSAI delivery with a Wowza Streaming Engine live stream](https://www.wowza.com/docs/insert-ad-markers-for-ssai-delivery-with-a-wowza-streaming-engine-live-stream)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/developer) to learn more about our APIs and SDK.

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](/LICENSE.txt).