# Ambient Room v1 contract

Status: frozen interface, version 1.0.0. This document supplies the behavior behind
`openapi.json`. Neither file asserts that an implementation has passed verification.
The independently releasable Quiet Launcher has no dependency on this API.

## Scope and ownership

Ambient Room includes looping library playback and resume; six scene presets;
clock/date and local dim settings; timer duck/chime/continue behavior; queue edits;
host messages and photos; guest QR join, library requests and host moderation.
Play, pause, next and volume work from the host remote and TV. Presets are `calm`,
`nightcity`, `rain`, `cafe`, `festival`, `kpop`; unknown imported moods map to `calm`.
Scene animation, weather effects and festival decoration may be local/manual.
No automatic location or external weather/calendar service is included.

No acquisition, external search, URL/media import, downloader, video, karaoke,
voice service, updater, installer, telemetry or private application integration
is included. No inherited accounts, profiles or databases are migrated. Artwork
and fonts ship only after separate redistribution review; neutral replacement
art is acceptable without changing the protocol.

One coordinator owns one room and one active TV renderer in v1. Pairing another
TV explicitly replaces and revokes the previous one. It owns identifiers, provider
mapping, selected playlist links, local playlist overrides, queue, timer, message,
photo selection, party, credentials and all revisions. It persists these in a
new database with its own schema. No preexisting application schema is required.
TV owns the media engine, actual track/position, local monotonic deadlines and
rendering. Web controls call the coordinator; they never talk to providers.
There is no broadcast, multi-room or synchronized multi-TV behavior in v1.

Adapters expose only library lists, playlists, tracks, library search and byte
streams. Upstream libraries are read-only. Queue save writes a coordinator-local
playlist override; reset drops the session edit and uses that saved override,
or upstream order when no override exists. It never mutates an upstream playlist.
The host UI must label this action "Save local order". Removing a queue entry
never deletes a media file. Playlist links are explicit; no special playlist
name is assumed. Maximum 20 links and 5,000 tracks per effective playlist/queue;
oversized upstream playlists return `409 conflict` without silent truncation.

## First run and identities

Ship no configured origin, endpoint, profiles, server credentials or paired device.
The coordinator starts unconfigured. Its local console displays a random one-use
setup code (at least 128 bits, expires after five minutes). `POST /v1/setup` requires
that code, a user-entered public coordinator origin and library configuration.
Validation, connectivity check and configuration commit succeed atomically or
leave setup available. Once configured, setup returns `409 setup_complete`.
Subsequent host login uses a separate one-use five-minute approval code from the
local console through `POST /v1/auth/host`. No default password or hard-coded code.
Codes are never placed in URLs. Console recovery can revoke all tokens and issue
a fresh host approval code without deleting playlists; no remote reset endpoint.

Host UI stores its bearer in memory for the session. Closing the page requires
another console approval. Host tokens expire after 12 hours, can revoke themselves,
and cannot approve themselves for extension. Library credentials are accepted
only by setup or an authenticated host configuration write; status reads never
echo them. Credentials reside in an access-restricted coordinator store. Provider
errors and logs redact secrets and addresses. Configuration replacement preserves
local IDs only when the same library identity is verified; otherwise clears links,
queues, resume, photos and active party atomically and stops playback.

TV first run asks for the coordinator origin and displays an eight-character
pairing code from `POST /v1/pairings`. The user opens the host UI and approves it.
The TV polls `POST /v1/pairings/{pairId}/claim` using a distinct random claim secret;
knowledge of the displayed code or pair ID alone never yields a device token.
Pairing expires in five minutes and polls at the returned interval (minimum two
seconds). Approval requires an unexpired host token and a confirmation that a
current TV will be replaced. Replacement revokes the previous device immediately.
The approved claim returns a 30-day TV token and renderer `sessionId`; retrying
the identical claim may return the same result until the five-minute pairing
deadline, then destroys the claim record. Tokens do not automatically renew.
Re-pair after expiry or revocation. Android stores device credentials in private
app storage, excluded from backup. Unpair clears them and cancels playback.

Use the neutral names/package/storage IDs in `identities.json`. They are frozen
local identifiers, not assertions of registry, domain or store availability.
Rights-holder attribution is a separate licensing decision; a neutral product
name does not replace accurate legal notices.

## Transport and authorization

All routes are under `/v1`, with no legacy aliases. Deploy the web UI at the
coordinator origin. TLS is the default; insecure local HTTP requires an explicit
user choice during setup and TV configuration. No default remote address, network
discovery or host-name allowlist is inherited. Origin and provider URL validation
allows only user-supplied HTTP(S) origins, rejects embedded credentials, fragments
and arbitrary redirect targets, and prevents provider results from choosing a
different origin. Provider base URLs may include an installation path. CORS is
same-origin; no wildcard. Web pages send no third-party requests and use a
no-referrer policy. Tokens, photos and API responses use `Cache-Control: no-store`.

`hostBearer` grants room control/configuration/moderation; `deviceBearer` grants
snapshot/selected playlists/streams, playback/timer/volume and QR status. It never
grants library configuration, photo uploads, host messages or moderation.
Guest API uses `x-party-token` and, after joining, `x-guest-key`. A party token is
not a host or device token. All permissions, live party membership and media
ownership are checked before idempotency lookup or body buffering. Unauthenticated
requests are `401`; authenticated wrong role `403`; room/media ownership mismatch
`404`; dead guest session `410 ended`; disabled guest mode `403 off`; blocked guest
`403 blocked`. A blocked key is refused even on state reads and rejoin attempts.
Revocation takes effect on the next request and interrupts active media responses.
The renderer clears credentials and stops playback on device `401`; it never
falls back to another identity. Configured HTTPS origins must not downgrade.

Unknown request properties and query parameters are rejected (`400 bad_request`).
IDs are opaque strings matching the schema, never provider URLs or numeric aliases.
Times are integer epoch milliseconds on the coordinator clock; durations/positions
explicitly end in `Sec`. Nullable fields are present as null, rather than omitted.
Success is 200, including pairing pending and idempotent deletes. Errors have the
closed `Error` body; 429 adds integer-seconds `Retry-After`. Upstream failure is
503 `upstream_unavailable` with a generic message, never a provider error dump.
Photo failures use 413 `too_big`, 415 `bad_type`, 422 `bad_image`.

Conservative fixed-window limits per minute: pairing start 10 per client address,
pair claim 30 per pair; setup/login/approve failures 10 per client address; state
240 and device writes 60 per device; host ordinary writes 30 per host, messages
and uploads 6 each per host, moderation 60 per host; guest reads 120, joins 20 and
search 30 per client address, adds 15 and controls/removals 20 per guest key.
Apply both address and actor limits where available. At most 100 guests per party.
These are standalone abuse limits, not a private network trust model.

## Revisions, retry and renderer lifecycle

Every authenticated mutating operation requires `Idempotency-Key`. It is scoped
to the authenticated principal, method and path; hash the canonical body too.
Same key/body repeats the original response without another mutation; same key
with different body returns `409 conflict`. Persist successful records for 24
hours; purge guest records when the party ends. For binary uploads use the body
hash. Failed validations do not reserve keys. Do not cache transient failures.
Join uses the party token as principal and replays the same guest key response.
Setup and login are protected by one-use codes, pairing claim by claim secret,
rather than this header. No retry may bypass expiry, revocation or blocking.

`seq` monotonically increases for observable room mutations, including completion
of the matching timer, local edits and explicit playback commands. Preserve it
across restarts. Clients ignore states with lower `seq`. Resume samples and gain
measurement changes do not increment `seq`; `gainRev` independently increases
when the gain array changes and must be applied on every response. `cmd.n` is a
persistent monotonic counter incremented once per accepted control request, even
if the action is the same. On entry, baseline the existing command number without
executing it; thereafter execute only strictly greater `n`. The snapshot's paused
and resume fields restore the current state, so baselining cannot lose pause.
Client acknowledgment of its own mutation response prevents replay on the next
poll. TV suppresses repeated physical key-down events for play/pause and actions.

`GET /v1/state?since=N&waitSec=25` may hold until `seq` advances, gain revision
changes or timeout. First read has no `since`; fallback polling is every five
seconds. Never request more than once per second when a server responds early.
Long-poll network timeout is at least `waitSec + 10` seconds. QR polls every 15
seconds independently. Back off network failures, keeping current buffered music
and local expiry enforcement. `tvConnected` means a device state read or resume
sample arrived in the last 30 seconds. Start and setTimer require a live paired
renderer (`409 no_tv`); starting a missing/empty selected playlist is 404/409.
State reads never automatically start playback. Timer while idle returns 409
`not_playing`; the host can explicitly start first. Default start chooses valid
resume playlist, then first linked playlist. Stop is idempotent, clears active
playback and timer, and ends the party immediately. Pause keeps the party alive.

Resume every 15 seconds, on track change and before stop/exit. Coordinator accepts
only samples from the currently active renderer `sessionId` whose `sampleN` is
greater than the last stored sample. It validates playlist/track membership and
clamps position to duration. The pairing-issued session lasts for the entire
device credential lifetime; start does not replace it. A coordinator restart
preserves it; a re-pair issues a new session. TV
persists its next sample counter with its token, so app restarts cannot rewind
the counter. This avoids device wall-clock ordering. Late samples after stop or
playlist replacement are refused as `stored:false` and cannot restart playback.

## Timer, message and photo behavior

Timer accepts whole 1–240 minutes, presets 5/15/30/60 plus Off. Each set creates a
new immutable timer ID. Translate `endsAt - serverNow` at request-send monotonic
time, conservatively including request/render delay; never extend lifetime based
on a delayed response or device wall-clock changes. Once finished, that ID cannot
be resurrected by stale reads. At expiry duck music to 20% over 1,200 ms, ring
softly twice 2,400 ms apart, hold for 2,000 ms after the second chime, restore the
previous effective volume over 2,500 ms, hide the countdown and continue playback.
Preserve a user pause and base volume, including volume zero.
An expiry more than 20 seconds stale finishes silently. Complete with
`POST /v1/timer/complete {timerId}`; a different/newer ID is ignored. Explicit Off
clears the timer without ringing; during an already-started ring, finish restoring
volume even if the matching timer disappears. After two minutes past expiry the
coordinator may clear it as a safety net; it never stops music. An older broad
clear endpoint is not used to acknowledge expiry, so it cannot cancel a new timer.

Messages have style banner/card/confetti, default UI choices banner and 30 minutes.
Clients send explicit style and minutes (1–240). Before checking the 1–60 Unicode
code-point length, replace controls U+0000–001F, U+007F–009F, U+061C, U+200B,
U+200E–200F, U+202A–202E, U+2060–206F, U+FEFF, U+FFF9–FFFB with spaces, collapse
whitespace, trim, normalize NFC. Preserve U+200C and U+200D. TV hides messages at
their monotonic expiry without waiting for a new revision. Coordinator omits
expired content by returning null even if `seq` is unchanged. Render as text.

Photo upload accepts only signature-verified JPEG/PNG/WebP, 1–8,388,608 bytes,
at most 40,000,000 decoded pixels, and positive dimensions. Authenticate/rate-limit
before buffering. Decode on CPU under bounded memory/time; reject malformed or
animated inputs. Apply orientation, resize longest side to at most 1920 without
enlarging, encode a fresh JPEG, and remove all metadata. Keep the selected photo
and at most two other recent photos; on a new upload select it and evict the
oldest unselected photo. IDs are random, room-scoped, and never paths. Picture
selection is exactly one of `{scene}`, `{uploadId}`, or `{}` for playlist scene.
Fetch by authorized picture route; no public/static upload directory. Failed
upload leaves current selection intact and no partial file. No live photos are
fixtures. Disconnect/unpair deletes all room photo bytes and selection.

## Queue and guest party

Queues loop; each track appears once. Add an existing track moves it. `next`
inserts immediately after current; `end` immediately before current on the next
wrap (array end when current unknown). Move places before `beforeId`; null means
end of loop. Moving an unknown track/beforeId returns 404. Removing the currently
playing track retains its buffered item until natural transition, while the
coordinator records the successor anchor. The renderer reconciles without
restarting the current item. Empty queue pauses playback and returns no resume;
it does not end the active party. Reset applies saved local/upstream order.
Changing playlist, or starting more than 12 hours after the last session edit,
drops the session edit. Save local order requires a nonempty queue. Adapter
search and host queue adds may use any track in the configured library.

Guest mode defaults off. Starting playback with mode enabled creates a random
party token, stable until stop, renew, unpair or 12-hour expiry. Expiry does not
create a new party silently; host Renew explicitly starts a new one while a
playlist is active. Enabling guest mode during active playback creates a party
if none exists. Disabling hides the QR and refuses guest calls immediately;
re-enabling restores a still-unexpired party token. Renew invalidates the old
token, guest keys, membership, pending requests and join-idempotency records;
remove pending guest tracks, preserving current playback and host queue entries.
Party expiry/stop also invalidates them and purges guest identity/request data.
Unpair ends the party and clears photos. Coordinator startup never revives a
party whose expiry or stop was already recorded.

QR is a user-configured coordinator origin plus `/guest#t=<party-token>`, never
a private default or a query parameter. Web extracts the fragment, removes it
from visible history, and sends headers. No access logs contain tokens or keys.
Guest key persists only in sessionStorage under the neutral storage prefix and
is cleared on terminal expiry/blocked/off responses. It expires with the party.
No account sign-in or identity lookup. Name is cleaned like message text, 1–24
Unicode code points. A fresh name is a display label, not proof of identity.

Guest token alone can read an anonymous guest state and join. Search and writes
require a joined, unblocked guest key. Search is strictly library-only, 1–120
characters and 30 results; `online`, external IDs and URL fields are rejected.
Duplicate pending/playing requests for a track return `409 already`; a guest may
request it again after it played. Requests are queued/playing/played/removed,
with no fetching/download state. A guest may remove only their waiting request
and control only their own currently playing requested track; otherwise
`403 not_yours`. Guest responses never expose host snapshots, other guest keys,
provider identifiers, credentials or configuration.

Host can remove any request, block/unblock a guest, switch modes and renew.
Removing a playing request skips immediately (unlike ordinary queue removal).
Blocking removes that guest's waiting requests and skips their current request;
all further calls with that key fail. Unblocking restores eligibility, not the
removed requests. A new anonymous browser can join under another name; blocking
is moderation, not strong personal identity enforcement.

First-come sorts waiting requests by monotonic request sequence. Take-turns groups
each guest's requests in request order and interleaves one per guest per round;
within each round, guest whose latest played request sequence is smallest goes
first (never played = zero), then oldest waiting request. Reordering affects
waiting guest positions, preserves current playback and relative host entries.
Map reordered guest requests to their existing waiting guest slots. A new guest
request takes the next unoccupied slot immediately after the current item and
the already-waiting guest block, before remaining host entries. The host view
contains guest/request IDs and labels for moderation; guest view contains at
most 40 upcoming entries with `mine` indicators. Provider membership is checked
again at add/stream time; missing track is 404, not an external fetch fallback.

## TV keys and launcher boundary

TV OK toggles play/pause; media Play and Pause are explicit, Play/Pause toggles.
Down/Menu opens timer menu, Left/Right selects, OK applies, Up/Menu/Back closes.
Back closes the menu before it leaves playback. Home and lifecycle exit save
resume, call stop and release player resources. Any key wakes dimming; when
dimmed, OK/arrows/Menu only wake, media keys also act, Back wakes and exits.
Other keys pass to the system. Keep clock, messages and QR clear of each other.
No integration with another launcher's tabs or remote bridge is included.

Quiet Launcher is a separate offline Android project. On first run create
"Profile One" with an empty app selection; let the user choose installed
launchable apps and create/rename/reorder generic local profiles. Discover using
targeted MAIN/LEANBACK_LAUNCHER and MAIN/LAUNCHER intent queries, deduplicate by
package and use installed labels/icons. No broad package visibility permission,
network permission, private web shortcuts, server shelf or preset third-party
app list. Missing app gives a local notice and allows reselection. First focus
follows profile order; Up reaches profile control, Down returns; Back on root
is a no-op, Back in picker/profile UI closes it; Home restores root focus without
starting duplicate activities. Include a generic system Settings action and
recovery instructions through device Settings. Store identity is independent of
Ambient Room. Device/OEM behavior and Home-role recovery remain device checks.

## Verification boundary

Fixtures are synthetic and identify expected schema/transition behavior only.
`fixtures/verification-matrix.json` specifies downstream mock and screenshot
gates. P2 and P6 must pass the same vectors; P5 tests the implementations with
isolated mock adapters and generated media, denied external network, no device,
GPU or live services. Any contract change requires a new freeze revision and
updated schema, fixtures and both consumers before assembly.
