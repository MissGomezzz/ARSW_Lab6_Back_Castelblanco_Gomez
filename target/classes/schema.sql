```sql
CREATE TABLE IF NOT EXISTS blueprints (
    author VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    points JSONB NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY (author, name)
);

INSERT INTO blueprints (author, name, points) VALUES

    (
        'john',
        'house',
        '[{"x":80,"y":300},{"x":80,"y":160},{"x":160,"y":90},{"x":240,"y":160},{"x":240,"y":300},{"x":80,"y":300}]'::jsonb
    ),

    (
        'john',
        'garage',
        '[{"x":300,"y":300},{"x":300,"y":200},{"x":460,"y":200},{"x":460,"y":300},{"x":300,"y":300}]'::jsonb
    ),

    (
        'jane',
        'garden',
        '[{"x":40,"y":320},{"x":100,"y":260},{"x":160,"y":300},{"x":220,"y":240},{"x":280,"y":280},{"x":340,"y":220},{"x":400,"y":260},{"x":460,"y":200}]'::jsonb
    ),

    (
        'jane',
        'pool',
        '[{"x":120,"y":120},{"x":400,"y":120},{"x":400,"y":240},{"x":120,"y":240},{"x":120,"y":120}]'::jsonb
    ),

    (
        'samuel',
        'star',
        '[{"x":260,"y":50},{"x":292,"y":136},{"x":384,"y":140},{"x":312,"y":197},{"x":336,"y":285},{"x":260,"y":235},{"x":184,"y":285},{"x":208,"y":197},{"x":136,"y":140},{"x":228,"y":136},{"x":260,"y":50}]'::jsonb
    ),

    (
        'angela',
        'bridge',
        '[{"x":40,"y":260},{"x":120,"y":200},{"x":200,"y":180},{"x":260,"y":175},{"x":320,"y":180},{"x":400,"y":200},{"x":480,"y":260}]'::jsonb
    )

ON CONFLICT (author, name) DO NOTHING;
```
