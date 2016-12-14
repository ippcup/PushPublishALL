# PushPublishALL
This module is intended to be deployed on the Wowza live origin server(s) running the Wowza Transcoder.  It’s responsible for RTMP push publishing all matching streams to all configured edge servers along with all configured edge server applications.  It uses rtmp authentication to establish connections to edge servers, so the live edge servers will need to be configured as Wowza Live Origin servers and a publish.password file should be defined.
 
This was developed to address the need to support HLS without having the limitation of only 2 origin servers per edge server application and it provides a method for offloading HLS packetization to only the edge servers.  

It also implements a method for differentiating between Wowza edge servers that are bandwidth constrained (WAN clients) and those that are not (LAN clients).  Important details about this feature, along with its property definition:

- pushHostsMonitored defines a list of edge hosts whose connection counts need to be monitored

- If any of the connection count thresholds (pushRemoveThreshold1 | pushRemoveThreshold2) have been breached on any server listed for pushHostsMonitored, it will stop publishing the corresponding streamname (pushStreamNamesThreshold1 | pushStreamNamesThreshold2) on each server listed for pushHostsMonitored.

- NOTE: this particular feature requires a setInterval/clearInterval runnning every 2-4 seconds in your player javascript and compares the current position against a previously stored position.  If the two match for more than a desirable number of seconds, reload the player (and m3u8 manifest).  This prevents excessive buffering when the client suddenly loses the stream it was trying to play.  See prerequisites... 



### Prerequisites
- Installed on the Wowza Live Origin server(s) that has/have a valid Transcoder license and related stream name group containing renditions matching "pushStreamNamesThreshold0" from Application.xml
- Edge servers have the [AutoGenerateSMIL](https://github.com/ippcupttocs/AutoGenerateSMIL) module properly installed and are configured as Wowza live origins with matching publishing authentication information for "pushStreamUser" and "pushStreamPass" in their conf/publish.password file

## JWPlayer: 
```
function startBufferMonitor(streamHlsUri) {
    //"use strict";
    function bufferTimeout() {
        playerState = jwplayer("player").getState();
        if (playerState.match("buffering")) {
            onBufferAttempt++;
            if (onBufferAttempt > 4 && onBufferReload < 3) {
                console.log("buffer event number:"+onBufferAttempt);
                console.log("reload: "+streamHlsUri);
                onBufferAttempt=0;
                jwplayer("player").load({"file": streamHlsUri,"autostart": "true"});
                onBufferReload++;
            }
            if (onBufferAttempt > 4 && onBufferReload >= 3) {
                console.log("onBufferAttempt event#"+onBufferAttempt+" onBufferReload event#"+onBufferReload);
                jwplayer("player").load({"file": bufferMp4,"autostart": "true"});
                console.log("stop buffertimeoutHandler");
                console.log("start playing:"+bufferMp4);
                clearInterval(buffertimeoutHandler);
                buffertimeoutHandler=undefined;
            }
            else {
                console.log("onBufferAttempt event#"+onBufferAttempt+" onBufferReload event#"+onBufferReload);
            }
        }
        else if (playerState.match("playing")) {
            onBufferAttempt=0;
            console.log("stop buffertimeoutHandler");
            clearInterval(buffertimeoutHandler);
            buffertimeoutHandler=undefined;
        }
        else {
            onBufferAttempt=0;
            console.log("bufferTimeout - playerState:"+playerState);
            console.log("stop buffertimeoutHandler");
            clearInterval(buffertimeoutHandler);
            buffertimeoutHandler=undefined;
        }
    }
    if (typeof onBufferAttempt === "undefined" || onBufferAttempt > 6) {
        onBufferAttempt = 0;
    }
    buffertimeoutHandler = setInterval(bufferTimeout, 1000);
}
function startMonitor(mediaPrevPosition,streamHlsUri) {
    //"use strict";
    function posTimeout(){
        playerState = jwplayer("player").getState();
        if (playerState.match("playing")) {
            if (mediaPrevPosition === Math.round(jwplayer("player").getPosition()) && mediaPrevPosition !== 0) {
                console.log(playerState+" values are equal - reloading manifest:"+streamHlsUri+" - mediaPrevPosition:"+mediaPrevPosition+" mediaPosition:"+Math.round(jwplayer("player").getPosition()));
                clearInterval(mediatimeoutHandler);
                mediatimeoutHandler=undefined;
                playerState=undefined;
                jwplayer("player").load({"file": streamHlsUri,"autostart": "true"});
            }
            /*FOR DEBUG ONLY
            else {
                console.log(playerState+" continue monitor - mediaPrevPosition:"+mediaPrevPosition+" mediaPosition:"+Math.round(jwplayer("player").getPosition()));
            }
            FOR DEBUG ONLY */
            mediaPrevPosition = Math.round(jwplayer("player").getPosition());
        }
        else if (playerState.match("idle")){
            console.log("playerState:",playerState," - mediaPosition:",Math.round(jwplayer("player").getPosition())," mediaPrevPosition:",mediaPrevPosition);
            console.log("stop mediatimeoutHandler");
            clearInterval(mediatimeoutHandler);
            mediatimeoutHandler=undefined;
        }
        else if (playerState.match("buffering")) {
            console.log("playerState:",playerState," - mediaPosition:",Math.round(jwplayer("player").getPosition())," mediaPrevPosition:",mediaPrevPosition);
            var jwPlaylist = jwplayer("player").getPlaylist()[0].file;
            if (jwPlaylist !== errorMp4 && jwPlaylist !== bufferMp4) {
                if (typeof buffertimeoutHandler === "undefined") {
                    console.log("start buffertimeoutHandler");
                    startBufferMonitor(streamHlsUri);
                }
            }
            console.log("stop mediatimeoutHandler");
            clearInterval(mediatimeoutHandler);
            mediatimeoutHandler=undefined;
        }
        else {
            console.log("playerState:",playerState," - mediaPosition:",Math.round(jwplayer("player").getPosition())," mediaPrevPosition:",mediaPrevPosition);
            if (playerState.match("complete")) {
                console.log("stop mediatimeoutHandler");
                clearInterval(mediatimeoutHandler);
                mediatimeoutHandler=undefined;
            }
        }
    }
    if (typeof mediatimeoutHandler === "undefined") {
        console.log("start mediatimeoutHandler",mediaPrevPosition);
        mediaPrevPosition = Math.round(jwplayer("player").getPosition());
        mediatimeoutHandler = setInterval(posTimeout, 2000);
    }
    else {
        console.log("mediatimeoutHandler already running. playerState:",jwplayer("player").getState(),mediaPrevPosition);
    }
}
 
streamHlsUri = "http://wowzaedgeservervip.org:1935/live/_definst_/smil:1234567890.smil/manifest.m3u8";
onBufferReload = 0;
bufferMp4 = "mp4/onBuffer.mp4";
errorMp4 = "mp4/onError.mp4";
thumbNail = thumb;
hasPlayed = false;
jwplayer("player").onPlay(function() {
        var jwPlaylist = jwplayer("player").getPlaylist()[0].file;
        if ((!hasPlayed) && jwPlaylist !== bufferMp4) {
                if (typeof mediatimeoutHandler === "undefined") {
                        hasPlayed = true;
                        mediaPrevPosition = Math.round(jwplayer("player").getPosition())
                        setTimeout(function() {
                                console.log("start startMonitor");
                                startMonitor(mediaPrevPosition,streamHlsUri);
                        }, 2000);
                }
        }
});
```

## HTML5: 
```
var playerElement = document.getElementById("player");
playerElement.currentTime;
//Same logic as JWPlayer :)
```

Edit your conf/VHost.xml file as follows

## HTTPProviders
```
<HTTPProvider>
        <BaseClass>com.wowza.wms.http.HTTPProviderMediaList</BaseClass>
        <RequestFilters>*jwplayer.rss|*jwplayer.smil|*medialist.smil|*manifest-rtmp.f4m</RequestFilters>
        <AuthenticationMethod>none</AuthenticationMethod>
</HTTPProvider>
```


Edit your conf/[appname]/Application.xml as follows

## Modules
```
<Module>
	<Name>PushPublishALL</Name>
	<Description>Publishes all live streams to edge servers</Description>
	<Class>org.mycompany.wowza.module.PushPublishALL</Class>
</Module>
```


## Properties
```
<Property>
	<Name>pushHosts</Name>
	<!-- Comma seperated list of edge servers to push publish pushStreamNamesThreshold0 to  -->
	<Value>server1,server2,server3,server4,server5,server6,server7,server8,server9,server10,server11,server12,server13,server14,server15,server16,server17,server18</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushHostsMonitored</Name>
	<!-- Comma seperated list of edge servers to receive connectioninfo from and unPublish streams based on pushRemoveThreshold[1-2]  -->
	<Value>server1,server2,server3,server4,server5,server6,server7,server8,server9,server10,server11,server12</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushHostsCheckInt</Name>
	<!-- Interval in ms to check http://pushHostsMonitored:8086/connectioninfo   -->
	<Value>30000</Value>
	<Type>Integer</Type>
</Property>
<Property>
	<Name>pushRemoveThreshold1</Name>
	<!-- When connectioninfo equals this value on at least one pushHost, -->
	<Value>600</Value>
	<!-- streams defined in pushStreamNamesThreshold1 will be removed from all pushHosts  -->
	<Type>Integer</Type>
</Property>
<Property>
	<Name>pushRemoveThreshold2</Name>
	<!-- When connectioninfo equals this value on at least one pushHostsMonitored, -->
	<!-- streams defined in pushStreamNamesThreshold2 will be removed from all pushHostsMonitored  -->
	<Value>1000</Value>
	<Type>Integer</Type>
</Property>
<Property>
	<Name>pushStreamNamesThreshold0</Name>
	<!-- Default pipe-delimited set of streamname suffixes to be push published to all pushHosts -->
	<!-- Order should match the order listed in the transcoder's stream name group  -->
	<Value>_720p|_360p|_240p|_160p</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushStreamNamesThreshold1</Name>
	<!-- When pushRemoveThreshold1 has been met, remove the defined single, or pipe-delimited set of streams  -->
	<Value>_720p</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushStreamNamesThreshold2</Name>
	<!-- When pushRemoveThreshold2 has been met, remove the defined single, or pipe-delimited set of streams  -->
	<Value>_720p|_360p</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushHostsPort</Name>
	<!-- Non-SSL enabled port on the edge servers that the push publisher can connect to -->
	<Value>80</Value>
	<Type>Integer</Type>
</Property>
<Property>
	<Name>pushHostsAppName</Name>
	<!-- Single or comma-seperated list of edge server application names that push publisher will send to -->
	<!-- <Value>live</Value> -->
	<Value>live</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushStreamSource</Name>
	<!-- regex matching the streamname defined in the encoder before it's sent to the origin. In this case, any integer -->
	<Value>^[0-9]++</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushIsAdaptive</Name>
	<!-- Instructs push publisher that the streams should be keyframe aligned  -->
	<Value>true</Value>
	<Type>Boolean</Type>
</Property>
<Property>
	<Name>pushSendOriginalTC</Name>
	<!-- Instructs push publisher that the streams should be keyframe aligned  -->
	<Value>true</Value>
	<Type>Boolean</Type>
</Property>
<Property>
	<Name>pushTCThreshold</Name>
	<!-- Instructs push publisher that the streams should be keyframe aligned  -->
	<Value>0x100000</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushEnableDebugLog</Name>
	<!-- Enables debug logging for push publisher module -->
	<Value>false</Value>
	<Type>Boolean</Type>
</Property>
<Property>
	<Name>pushEnableDebugPackets</Name>
	<!-- Debug logging for push publish live packets  -->
	<Value>false</Value>
	<Type>Boolean</Type>
</Property>
<Property>
	<Name>pushStreamUser</Name>
	<Value>user</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushStreamPass</Name>
	<Value>changeit!</Value>
	<Type>String</Type>
</Property>
```

## More resources
[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.
