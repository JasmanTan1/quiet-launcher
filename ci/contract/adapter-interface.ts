/** Frozen v1 boundary. No network credentials or upstream addresses cross it. */
export type Id = string;
export type Scene = 'calm' | 'nightcity' | 'rain' | 'cafe' | 'festival' | 'kpop';
export interface Track {
  id: Id;
  title: string;
  artist: string;
  durationSec: number;
  gainDb: number | null;
}
export interface PlaylistInfo {
  id: Id;
  name: string;
  scene: Scene;
  trackCount: number;
}
export interface Playlist extends Omit<PlaylistInfo, 'trackCount'> {
  tracks: Track[];
}
export interface LibraryPage {
  playlists: PlaylistInfo[];
  /** Private adapter cursor; never a URL. Null when exhausted. */
  cursor: string | null;
}
export interface ByteRange {
  /** One HTTP bytes range, validated by coordinator before provider dispatch. */
  header: string;
}
export interface MediaBytes {
  status: 200 | 206;
  contentType: string;
  contentLength: number | null;
  contentRange: string | null;
  acceptRanges: 'bytes';
  /** CPU-only stream; abort immediately when authorization is revoked. */
  body: AsyncIterable<Uint8Array>;
}
export type AdapterErrorCode =
  | 'not_found' | 'unauthorized' | 'unavailable' | 'bad_response' | 'range_unsatisfiable';
export interface AdapterError extends Error {
  code: AdapterErrorCode;
  /** Safe generic text only; coordinator maps these to public Error bodies. */
  message: string;
}
export interface LibraryAdapter {
  readonly kind: 'opensubsonic' | 'jellyfin';
  /** Ping is explicit setup/status activity, never background discovery. */
  check(signal: AbortSignal): Promise<void>;
  listPlaylists(cursor: string | null, limit: number, signal: AbortSignal): Promise<LibraryPage>;
  getPlaylist(id: Id, signal: AbortSignal): Promise<Playlist>;
  getTrack(id: Id, signal: AbortSignal): Promise<Track>;
  /** Configured library only. At most 30 tracks, no web/online fallback. */
  search(query: string, limit: number, signal: AbortSignal): Promise<Track[]>;
  openStream(id: Id, range: ByteRange | null, signal: AbortSignal): Promise<MediaBytes>;
}
export interface AdapterContext {
  /** Validated configured origin/path; private server configuration only. */
  baseUrl: URL;
  /** Accesses runtime secrets inside coordinator. Never serialize this object. */
  credentials: { username?: string; credential: string };
  /** Injected HTTP transport for mockability; restrict redirects to configured origin. */
  fetch: typeof globalThis.fetch;
  /** Maps provider-local IDs to persistent, URL-safe opaque coordinator IDs. */
  ids: {
    fromProvider(type: 'playlist' | 'track', providerId: string): Id;
    toProvider(type: 'playlist' | 'track', id: Id): string | null;
  };
}
export type AdapterFactory = (context: AdapterContext) => LibraryAdapter;

/* Implement separately for OpenSubsonic and Jellyfin REST. No SDK requirement,
 * acquisition hook, playlist-write method, private MusicService, provider URL in
 * Track, filesystem path or credential in a response. Keep dependency versions.
 * Adapter pagination must be fully drained before a selected playlist is used;
 * over-limit playlist is rejected explicitly. Missing/non-audio items are not
 * silently converted into external requests. Provider request authentication is
 * composed inside the adapter and must never appear in logs or redirect headers.
 * Null gains mean unity; stream supports one bytes range (including suffix and
 * open-ended forms), 416 on invalid/unsatisfiable range, and cancellation.
 * Fixtures exercise the interface with injected in-memory transport only.
 */
