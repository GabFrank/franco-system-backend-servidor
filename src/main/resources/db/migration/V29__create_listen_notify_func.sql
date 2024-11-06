-- Create the trigger function to send notifications
CREATE OR REPLACE FUNCTION notify_error_event() RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify(
        'error_event_channel',
        json_build_object(
            'table', TG_TABLE_NAME,
            'operation', TG_OP,
            'id', NEW.id,
            'message', 'A ' || TG_OP || ' operation occurred on table ' || TG_TABLE_NAME
        )::text
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
