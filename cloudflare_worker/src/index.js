var KV_NAMESPACE;
const firebaseProjectID = "YourFirebaseProjectIDHere";
const SERVICE_ACCOUNT_EMAIL = "YOUR SERVICE ACCOUNT EMAIL@YourFIREBASEID.iam.gserviceaccount.com";
const PRIVATE_KEY = `-----BEGIN PRIVATE KEY-----\nYOUR SERVICE ACCOUNT PRIVATE KEY HERE TO GENERATE APIS\n-----END PRIVATE KEY-----\n`;
    
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    KV_NAMESPACE = env.MY_KV_NAMESPACE;

    if (url.pathname === "/sendLocation") {
      return await handleSendLocation(url, env);
    }

    if (url.pathname === "/getLocation") {
      return await handleGetLocation(url, env);
    }

    if (url.pathname === "/ring") {
      return await handleRing(url, env);
    }

    if (url.pathname === "/stopRing") {
      return await handleStopRing(url, env);
    }

    return new Response("Not found", { status: 404 });
  }
};

async function handleRing(url, env) {
  const target = url.searchParams.get("target");

  if (!target) {
    return new Response("Missing parameters", { status: 400 });
  }

  const token = await getAPI();

  const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${firebaseProjectID}/databases/(default)/documents/users/${target}?mask.fieldPaths=fcmToken`;

  const res = await fetch(firestoreUrl, {
    headers: { "Authorization": `Bearer ${token}` }
  });

  if (res.status !== 200) {
    return new Response("Target user not found", { status: 404 });
  }

  const userDoc = await res.json();
  const fcmToken = userDoc.fields?.fcmToken?.stringValue;

  if (!fcmToken) {
    return new Response("No FCM token found for target", { status: 404 });
  }

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${firebaseProjectID}/messages:send`;

  const fcmBody = {
    message: {
      token: fcmToken,
      data: {
        type: "ring",
        target: target
      },
      android: {
        priority: "high"
      }
    }
  };

  const fcmRes = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(fcmBody)
  });

  const fcmResult = await fcmRes.json();

  return new Response(JSON.stringify({ status: "Notification sent", result: fcmResult }), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
}

async function handleStopRing(url, env) {
  const target = url.searchParams.get("target");

  if (!target) {
    return new Response("Missing parameters", { status: 400 });
  }

  const token = await getAPI();

  const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${firebaseProjectID}/databases/(default)/documents/users/${target}?mask.fieldPaths=fcmToken`;

  const res = await fetch(firestoreUrl, {
    headers: { "Authorization": `Bearer ${token}` }
  });

  if (res.status !== 200) {
    return new Response("Target user not found", { status: 404 });
  }

  const userDoc = await res.json();
  const fcmToken = userDoc.fields?.fcmToken?.stringValue;

  if (!fcmToken) {
    return new Response("No FCM token found for target", { status: 404 });
  }

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${firebaseProjectID}/messages:send`;

  const fcmBody = {
    message: {
      token: fcmToken,
      data: {
        type: "stopRing",
        target: target
      },
      android: {
        priority: "high"
      }
    }
  };

  const fcmRes = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(fcmBody)
  });

  const fcmResult = await fcmRes.json();

  return new Response(JSON.stringify({ status: "Notification sent", result: fcmResult }), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
}

async function handleSendLocation(url, env) {
  const target = url.searchParams.get("target");
  const latitude = url.searchParams.get("latitude");
  const longitude = url.searchParams.get("longitude");
  const battery = url.searchParams.get("battery");
  const sender = url.searchParams.get("sender");

  if (!target || !latitude || !longitude || !battery || !sender) {
    return new Response("Missing parameters", { status: 400 });
  }

  const token = await getAPI();

  const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${firebaseProjectID}/databases/(default)/documents/users/${sender}?updateMask.fieldPaths=lastKnown`;
  const firestoreUrlFCM = `https://firestore.googleapis.com/v1/projects/${firebaseProjectID}/databases/(default)/documents/users/${target}?mask.fieldPaths=fcmToken`;

  const res = await fetch(firestoreUrlFCM, {
    headers: { "Authorization": `Bearer ${token}` }
  });

  if (res.status !== 200) {
    return new Response("Target user not found", { status: 404 });
  }

  const userDoc = await res.json();
  const fcmToken = userDoc.fields?.fcmToken?.stringValue;

  if (!fcmToken) {
    return new Response("No FCM token found for target", { status: 404 });
  }

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${firebaseProjectID}/messages:send`;

  const fcmBody = {
    message: {
      token: fcmToken,
      data: {
        type: "sendLocation",
        sender: sender,
        latitude: latitude,
        longitude: longitude,
        battery: battery
      }
    }
  };

  const fcmRes = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(fcmBody)
  });

  const fcmResult = await fcmRes.json();

  const body = {
    fields: {
      lastKnown: {
        mapValue: {
          fields: {
            latitude: { doubleValue: parseFloat(latitude) },
            longitude: { doubleValue: parseFloat(longitude) },
            battery: { integerValue: parseInt(battery) },
            lastUpdatedBy: { stringValue: target },
            updatedAt: { timestampValue: new Date().toISOString() }
          }
        }
      }
    }
  };

  await fetch(firestoreUrl, {
    method: "PATCH",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  return new Response(JSON.stringify({ status: "Location Updated", result: fcmResult }), { status: 200 });
}

async function handleGetLocation(url, env) {
  const target = url.searchParams.get("target");
  const sender = url.searchParams.get("sender");

  if (!target || !sender) {
    return new Response("Missing parameters", { status: 400 });
  }

  const token = await getAPI();

  const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${firebaseProjectID}/databases/(default)/documents/users/${target}?mask.fieldPaths=fcmToken`;

  const res = await fetch(firestoreUrl, {
    headers: { "Authorization": `Bearer ${token}` }
  });

  if (res.status !== 200) {
    return new Response("Target user not found", { status: 404 });
  }

  const userDoc = await res.json();
  const fcmToken = userDoc.fields?.fcmToken?.stringValue;

  if (!fcmToken) {
    return new Response("No FCM token found for target", { status: 404 });
  }

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${firebaseProjectID}/messages:send`;

  const fcmBody = {
    message: {
      token: fcmToken,
      data: {
        type: "getLocation",
        sender: sender
      },
      android: {
        priority: "high"
      }
    }
  };

  const fcmRes = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(fcmBody)
  });

  const fcmResult = await fcmRes.json();

  return new Response(JSON.stringify({ status: "Notification sent", result: fcmResult }), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
}

async function getAPI() {
  const data = await KV_NAMESPACE.get("api_key", { type: "json" });
  if (data && !isExpired(data.timestamp)) {
    return data.access_token;
  }
  const access_token = await generateAPI();
  const newData = {
    access_token,
    timestamp: Date.now(),
  };
  await KV_NAMESPACE.put("api_key", JSON.stringify(newData));
  return data.access_token;
}

function isExpired(storedTime) {
  return Date.now() - storedTime > 55 * 60 * 1000;
}

async function generateAPI() {
    const jwtHeader = {
      alg: "RS256",
      typ: "JWT"
    };
    const nowSeconds = Math.floor(Date.now() / 1000);
    const jwtPayload = {
      iss: SERVICE_ACCOUNT_EMAIL,
      scope: "https://www.googleapis.com/auth/cloud-platform",
      aud: "https://oauth2.googleapis.com/token",
      exp: nowSeconds + 3600,
      iat: nowSeconds,
    };
    const encodedHeader = btoa(JSON.stringify(jwtHeader)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
    const encodedPayload = btoa(JSON.stringify(jwtPayload)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
    const dataToSign = `${encodedHeader}.${encodedPayload}`;
    const privateKeyData = PRIVATE_KEY.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\n/g, "");
    const privateKeyBuffer = strToArrayBuffer(atob(privateKeyData));
    const importedKey = await crypto.subtle.importKey(
      "pkcs8",
      privateKeyBuffer, {
        name: "RSASSA-PKCS1-V1_5",
        hash: "SHA-256"
      },
      false,
      ["sign"]
    );
    const signatureBuffer = await crypto.subtle.sign(
      { name: "RSASSA-PKCS1-V1_5" },
      importedKey,
      new TextEncoder().encode(dataToSign)
    );
    const encodedSignature = btoa(String.fromCharCode.apply(null, new Uint8Array(signatureBuffer)))
      .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
    const assertion = `${dataToSign}.${encodedSignature}`;
    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${assertion}`,
    });
    const responseData = await response.json();
    if (!response.ok || !responseData.access_token) {
      throw new Error(`Failed to get token: ${JSON.stringify(responseData)}`);
    }
    const accessToken = responseData.access_token;
    return accessToken;
}

function strToArrayBuffer(str) {
  const buf = new ArrayBuffer(str.length);
  const bufView = new Uint8Array(buf);
  for (let i = 0, strLen = str.length; i < strLen; i++) {
    bufView[i] = str.charCodeAt(i);
  }
  return buf;
}