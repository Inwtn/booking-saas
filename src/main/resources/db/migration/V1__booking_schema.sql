CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE app_users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL
);
CREATE TABLE workspaces (id uuid PRIMARY KEY, name varchar(100) NOT NULL);
CREATE TABLE memberships (
    workspace_id uuid NOT NULL REFERENCES workspaces(id),
    user_id uuid NOT NULL REFERENCES app_users(id),
    role varchar(10) NOT NULL CHECK (role IN ('OWNER','STAFF')),
    PRIMARY KEY (workspace_id,user_id)
);
CREATE TABLE resources (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces(id),
    name varchar(100) NOT NULL,
    UNIQUE (workspace_id,id)
);
CREATE TABLE bookings (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces(id),
    resource_id uuid NOT NULL,
    customer_name varchar(120) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(10) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','CANCELLED')),
    created_by uuid NOT NULL REFERENCES app_users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id,resource_id) REFERENCES resources(workspace_id,id),
    CHECK (ends_at > starts_at),
    EXCLUDE USING gist (resource_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE (status = 'CONFIRMED')
);
CREATE INDEX bookings_workspace_time ON bookings(workspace_id,starts_at);
