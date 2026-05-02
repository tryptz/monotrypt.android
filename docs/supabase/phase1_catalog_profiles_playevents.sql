-- Phase 1: Tidal-like schema — profiles, canonical catalog, play_events refactor.
-- Run in the Supabase SQL editor (production: lvzorvfhhopillzlwgau).
-- Idempotent: safe to re-run.
--
-- Scope:
--   1. profiles  — extend (avatar_url, country, tier) + auto-insert on auth.users
--   2. catalog_* — canonical artist/album/track tables (read-only for clients)
--   3. user_devices, play_sessions — new tables
--   4. play_events — add FK to catalog_tracks + session/device/started_at
--
-- Legacy denormalised columns on play_events are kept NULLABLE so the current
-- SupabaseSyncRepository keeps working during rollout. A follow-up migration
-- drops them once the client fully switches to catalog FKs.

-- =========================================================================
-- 0. Extensions + private schema for SECURITY DEFINER functions
-- =========================================================================
-- pg_trgm lives in `extensions` (not public) to satisfy advisor 0014.
create extension if not exists pg_trgm schema extensions;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- =========================================================================
-- 1. profiles — extend
-- =========================================================================
-- Existing cols: id, email, display_name, encrypted_metadata, created_at, updated_at
alter table public.profiles
    add column if not exists avatar_url text,
    add column if not exists country    text,
    add column if not exists tier       text not null default 'free';

-- tier domain guard (idempotent — drop + recreate)
alter table public.profiles drop constraint if exists profiles_tier_check;
alter table public.profiles
    add constraint profiles_tier_check
    check (tier in ('free', 'hifi', 'hifi_plus'));

-- Auto-create profile row on auth.users insert (single source of truth).
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, email, display_name, avatar_url)
    values (
        new.id,
        new.email,
        coalesce(
            new.raw_user_meta_data ->> 'full_name',
            new.raw_user_meta_data ->> 'name',
            split_part(coalesce(new.email, ''), '@', 1)
        ),
        new.raw_user_meta_data ->> 'avatar_url'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_user();

-- Backfill: any auth.users without a profile row.
insert into public.profiles (id, email, display_name)
select
    u.id,
    u.email,
    coalesce(
        u.raw_user_meta_data ->> 'full_name',
        u.raw_user_meta_data ->> 'name',
        split_part(coalesce(u.email, ''), '@', 1)
    )
from auth.users u
left join public.profiles p on p.id = u.id
where p.id is null;

-- =========================================================================
-- 2. Canonical catalog (artists, albums, tracks, source-ref mapping)
-- =========================================================================
create table if not exists public.catalog_artists (
    id             uuid primary key default gen_random_uuid(),
    name           text not null,
    picture_url    text,
    musicbrainz_id text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

create table if not exists public.catalog_albums (
    id                uuid primary key default gen_random_uuid(),
    title             text not null,
    cover_url         text,
    release_date      text,
    number_of_tracks  int,
    primary_artist_id uuid references public.catalog_artists(id) on delete set null,
    musicbrainz_id    text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create table if not exists public.catalog_tracks (
    id                uuid primary key default gen_random_uuid(),
    title             text not null,
    duration_s        int  not null default 0,
    album_id          uuid references public.catalog_albums(id)  on delete set null,
    primary_artist_id uuid references public.catalog_artists(id) on delete set null,
    audio_quality     text,
    isrc              text,
    explicit          boolean not null default false,
    track_number      int,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

-- The same canonical track can be reached from multiple sources.
-- source_ref encoding:
--   tidal       -> tidal track id (text)
--   collection  -> '<collection_uuid>:<file_hash>'
--   local       -> sha1(path)
create table if not exists public.catalog_track_sources (
    track_id   uuid not null references public.catalog_tracks(id) on delete cascade,
    source     text not null check (source in ('tidal', 'collection', 'local')),
    source_ref text not null,
    created_at timestamptz not null default now(),
    primary key (source, source_ref)
);

create index if not exists catalog_artists_name_trgm
    on public.catalog_artists using gin (name gin_trgm_ops);
create index if not exists catalog_albums_artist
    on public.catalog_albums (primary_artist_id);
create index if not exists catalog_tracks_album   on public.catalog_tracks (album_id);
create index if not exists catalog_tracks_artist  on public.catalog_tracks (primary_artist_id);
create index if not exists catalog_tracks_isrc    on public.catalog_tracks (isrc) where isrc is not null;
create index if not exists catalog_tracks_title_trgm
    on public.catalog_tracks using gin (title gin_trgm_ops);
create index if not exists catalog_track_sources_track
    on public.catalog_track_sources (track_id);

-- updated_at touch trigger
create or replace function private.touch_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists catalog_artists_touch on public.catalog_artists;
drop trigger if exists catalog_albums_touch  on public.catalog_albums;
drop trigger if exists catalog_tracks_touch  on public.catalog_tracks;

create trigger catalog_artists_touch
    before update on public.catalog_artists
    for each row execute function private.touch_updated_at();
create trigger catalog_albums_touch
    before update on public.catalog_albums
    for each row execute function private.touch_updated_at();
create trigger catalog_tracks_touch
    before update on public.catalog_tracks
    for each row execute function private.touch_updated_at();

-- RLS: authenticated users read everything; only service_role writes.
alter table public.catalog_artists       enable row level security;
alter table public.catalog_albums        enable row level security;
alter table public.catalog_tracks        enable row level security;
alter table public.catalog_track_sources enable row level security;

drop policy if exists catalog_artists_read       on public.catalog_artists;
drop policy if exists catalog_albums_read        on public.catalog_albums;
drop policy if exists catalog_tracks_read        on public.catalog_tracks;
drop policy if exists catalog_track_sources_read on public.catalog_track_sources;

create policy catalog_artists_read
    on public.catalog_artists for select to authenticated using (true);
create policy catalog_albums_read
    on public.catalog_albums for select to authenticated using (true);
create policy catalog_tracks_read
    on public.catalog_tracks for select to authenticated using (true);
create policy catalog_track_sources_read
    on public.catalog_track_sources for select to authenticated using (true);
-- No INSERT/UPDATE/DELETE policies: service_role bypasses RLS.

-- =========================================================================
-- 3. user_devices + play_sessions
-- =========================================================================
create table if not exists public.user_devices (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references auth.users(id) on delete cascade,
    platform     text not null check (platform in ('android', 'ios', 'web', 'desktop')),
    model        text,
    app_version  text,
    last_seen_at timestamptz not null default now(),
    created_at   timestamptz not null default now()
);

create index if not exists user_devices_user on public.user_devices (user_id);

alter table public.user_devices enable row level security;

drop policy if exists user_devices_select_own on public.user_devices;
drop policy if exists user_devices_insert_own on public.user_devices;
drop policy if exists user_devices_update_own on public.user_devices;
drop policy if exists user_devices_delete_own on public.user_devices;

create policy user_devices_select_own on public.user_devices
    for select using (auth.uid() = user_id);
create policy user_devices_insert_own on public.user_devices
    for insert with check (auth.uid() = user_id);
create policy user_devices_update_own on public.user_devices
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy user_devices_delete_own on public.user_devices
    for delete using (auth.uid() = user_id);

create table if not exists public.play_sessions (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references auth.users(id) on delete cascade,
    device_id   uuid references public.user_devices(id) on delete set null,
    started_at  timestamptz not null default now(),
    ended_at    timestamptz,
    track_count int not null default 0
);

create index if not exists play_sessions_user_started
    on public.play_sessions (user_id, started_at desc);

alter table public.play_sessions enable row level security;

drop policy if exists play_sessions_select_own on public.play_sessions;
drop policy if exists play_sessions_insert_own on public.play_sessions;
drop policy if exists play_sessions_update_own on public.play_sessions;
drop policy if exists play_sessions_delete_own on public.play_sessions;

create policy play_sessions_select_own on public.play_sessions
    for select using (auth.uid() = user_id);
create policy play_sessions_insert_own on public.play_sessions
    for insert with check (auth.uid() = user_id);
create policy play_sessions_update_own on public.play_sessions
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy play_sessions_delete_own on public.play_sessions
    for delete using (auth.uid() = user_id);

-- =========================================================================
-- 4. play_events — create with full shape (legacy + new canonical-catalog FKs).
--    Legacy denormalised columns stay so the existing SupabaseSyncRepository
--    push keeps working unchanged; a follow-up migration drops them once the
--    client fully switches to catalog FKs.
-- =========================================================================
create table if not exists public.play_events (
    id                 bigint generated by default as identity primary key,
    user_id            uuid not null references auth.users(id) on delete cascade,
    -- legacy denormalised fields
    track_id           bigint not null,
    title              text not null default '',
    duration           int  not null default 0,
    artist_id          bigint,
    artist_name        text not null default '',
    album_id           bigint,
    album_title        text,
    album_cover        text,
    audio_quality      text,
    source             text,
    played_at_ms       bigint not null default 0,
    inserted_at        timestamptz not null default now(),
    -- new canonical-catalog FKs
    track_uuid         uuid references public.catalog_tracks(id) on delete set null,
    session_id         uuid references public.play_sessions(id)  on delete set null,
    device_id          uuid references public.user_devices(id)   on delete set null,
    started_at         timestamptz default now(),
    duration_played_ms int,
    completed          boolean not null default false
);

-- If play_events already exists (pre-phase-1 deployment), add the new columns.
alter table public.play_events
    add column if not exists track_uuid         uuid references public.catalog_tracks(id) on delete set null,
    add column if not exists session_id         uuid references public.play_sessions(id)  on delete set null,
    add column if not exists device_id          uuid references public.user_devices(id)   on delete set null,
    add column if not exists started_at         timestamptz,
    add column if not exists duration_played_ms int,
    add column if not exists completed          boolean not null default false;

-- Backfill started_at from the legacy ms epoch column (no-op on fresh install).
update public.play_events
   set started_at = to_timestamp(played_at_ms / 1000.0)
 where started_at is null
   and played_at_ms is not null
   and played_at_ms > 0;

alter table public.play_events
    alter column started_at set default now();

alter table public.play_events enable row level security;

drop policy if exists play_events_select_own on public.play_events;
drop policy if exists play_events_insert_own on public.play_events;
drop policy if exists play_events_delete_own on public.play_events;

create policy play_events_select_own on public.play_events
    for select using (auth.uid() = user_id);
create policy play_events_insert_own on public.play_events
    for insert with check (auth.uid() = user_id);
create policy play_events_delete_own on public.play_events
    for delete using (auth.uid() = user_id);

create index if not exists play_events_user_played_at_ms
    on public.play_events (user_id, played_at_ms desc);
create index if not exists play_events_user_track
    on public.play_events (user_id, track_id);
create index if not exists play_events_user_started_at
    on public.play_events (user_id, started_at desc);
create index if not exists play_events_user_track_uuid
    on public.play_events (user_id, track_uuid);
create index if not exists play_events_session
    on public.play_events (session_id);

-- =========================================================================
-- 5. Lock down SECURITY DEFINER functions
-- =========================================================================
revoke execute on function private.handle_new_user()  from public, anon, authenticated;
revoke execute on function private.touch_updated_at() from public, anon, authenticated;

-- =========================================================================
-- 6. Performance: single-column FK covering indexes (advisor 0001)
-- =========================================================================
create index if not exists play_events_device_id
    on public.play_events (device_id);
create index if not exists play_events_track_uuid
    on public.play_events (track_uuid);
create index if not exists play_sessions_device_id
    on public.play_sessions (device_id);
create index if not exists user_playlists_user_id
    on public.user_playlists (user_id);

-- =========================================================================
-- 7. Performance: wrap auth.uid() in (select ...) so it is evaluated once
--    per query instead of once per row (advisor 0003). Applied to every
--    existing policy too, not just the new tables — this is a cheap win
--    on a table that currently has a few hundred rows but scales badly.
-- =========================================================================

-- profiles
drop policy if exists "Users can view own profile"   on public.profiles;
drop policy if exists "Users can update own profile" on public.profiles;
drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can view own profile"   on public.profiles
    for select using ((select auth.uid()) = id);
create policy "Users can update own profile" on public.profiles
    for update using ((select auth.uid()) = id)
                with check ((select auth.uid()) = id);
create policy "Users can insert own profile" on public.profiles
    for insert with check ((select auth.uid()) = id);

-- Per-user tables (all ALL policies)
drop policy if exists eq_presets_own       on public.eq_presets;
drop policy if exists mix_presets_own      on public.mix_presets;
drop policy if exists own_favorite_tracks  on public.favorite_tracks;
drop policy if exists own_favorite_albums  on public.favorite_albums;
drop policy if exists own_favorite_artists on public.favorite_artists;
drop policy if exists own_play_history     on public.play_history;
drop policy if exists own_local_folders    on public.local_folder_routes;
drop policy if exists own_playlists        on public.user_playlists;

create policy eq_presets_own       on public.eq_presets
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy mix_presets_own      on public.mix_presets
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy own_favorite_tracks  on public.favorite_tracks
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy own_favorite_albums  on public.favorite_albums
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy own_favorite_artists on public.favorite_artists
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy own_play_history     on public.play_history
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy own_local_folders    on public.local_folder_routes
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy own_playlists        on public.user_playlists
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- playlist_tracks: ownership checked via parent playlist
drop policy if exists own_playlist_tracks on public.playlist_tracks;
create policy own_playlist_tracks on public.playlist_tracks
    for all
    using (exists (
        select 1 from public.user_playlists p
        where p.id = playlist_tracks.playlist_id
          and p.user_id = (select auth.uid())
    ))
    with check (exists (
        select 1 from public.user_playlists p
        where p.id = playlist_tracks.playlist_id
          and p.user_id = (select auth.uid())
    ));

-- Re-wrap the new-table policies too (they were created with bare auth.uid()).
drop policy if exists user_devices_select_own on public.user_devices;
drop policy if exists user_devices_insert_own on public.user_devices;
drop policy if exists user_devices_update_own on public.user_devices;
drop policy if exists user_devices_delete_own on public.user_devices;
create policy user_devices_select_own on public.user_devices
    for select using ((select auth.uid()) = user_id);
create policy user_devices_insert_own on public.user_devices
    for insert with check ((select auth.uid()) = user_id);
create policy user_devices_update_own on public.user_devices
    for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy user_devices_delete_own on public.user_devices
    for delete using ((select auth.uid()) = user_id);

drop policy if exists play_sessions_select_own on public.play_sessions;
drop policy if exists play_sessions_insert_own on public.play_sessions;
drop policy if exists play_sessions_update_own on public.play_sessions;
drop policy if exists play_sessions_delete_own on public.play_sessions;
create policy play_sessions_select_own on public.play_sessions
    for select using ((select auth.uid()) = user_id);
create policy play_sessions_insert_own on public.play_sessions
    for insert with check ((select auth.uid()) = user_id);
create policy play_sessions_update_own on public.play_sessions
    for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy play_sessions_delete_own on public.play_sessions
    for delete using ((select auth.uid()) = user_id);

drop policy if exists play_events_select_own on public.play_events;
drop policy if exists play_events_insert_own on public.play_events;
drop policy if exists play_events_delete_own on public.play_events;
create policy play_events_select_own on public.play_events
    for select using ((select auth.uid()) = user_id);
create policy play_events_insert_own on public.play_events
    for insert with check ((select auth.uid()) = user_id);
create policy play_events_delete_own on public.play_events
    for delete using ((select auth.uid()) = user_id);
