<?php
/**
 * Chrome Browser - Screen Share Server
 * Version: 1.0.0
 * 
 * This PHP server enables screen sharing functionality
 * that can be viewed from any browser, not just the Chrome Browser app.
 * 
 * Features:
 * - WebRTC signaling server
 * - Room management
 * - Screen share viewer
 * - Real-time communication via WebSocket
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Handle preflight
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Configuration
define('ROOMS_DIR', __DIR__ . '/rooms');
define('MAX_ROOMS', 100);
define('ROOM_EXPIRY', 3600); // 1 hour

// Create rooms directory
if (!file_exists(ROOMS_DIR)) {
    mkdir(ROOMS_DIR, 0755, true);
}

// Get action
$action = $_GET['action'] ?? $_POST['action'] ?? '';

// Route handling
switch ($action) {
    case 'create_room':
        createRoom();
        break;
    case 'join_room':
        joinRoom();
        break;
    case 'leave_room':
        leaveRoom();
        break;
    case 'get_room':
        getRoom();
        break;
    case 'list_rooms':
        listRooms();
        break;
    case 'signal':
        handleSignal();
        break;
    case 'get_signals':
        getSignals();
        break;
    case 'update_offer':
        updateOffer();
        break;
    case 'get_offer':
        getOffer();
        break;
    case 'update_ice':
        updateIceCandidates();
        break;
    case 'get_ice':
        getIceCandidates();
        break;
    case 'cleanup':
        cleanupRooms();
        break;
    default:
        echo json_encode(['error' => 'Invalid action', 'available_actions' => [
            'create_room', 'join_room', 'leave_room', 'get_room', 'list_rooms',
            'signal', 'get_signals', 'update_offer', 'get_offer', 
            'update_ice', 'get_ice', 'cleanup'
        ]]);
}

// ==================== ROOM MANAGEMENT ====================

function createRoom() {
    $roomId = generateRoomId();
    $userId = generateUserId();
    $userName = $_POST['user_name'] ?? 'Host';
    
    $room = [
        'id' => $roomId,
        'created_at' => time(),
        'created_by' => $userId,
        'users' => [
            $userId => [
                'id' => $userId,
                'name' => $userName,
                'role' => 'host',
                'joined_at' => time(),
                'is_sharing' => false
            ]
        ],
        'screen_share' => [
            'active' => false,
            'host_id' => null,
            'offer' => null,
            'ice_candidates' => []
        ],
        'messages' => []
    ];
    
    saveRoom($roomId, $room);
    
    echo json_encode([
        'success' => true,
        'room' => $room,
        'your_user_id' => $userId
    ]);
}

function joinRoom() {
    $roomId = $_POST['room_id'] ?? '';
    $userName = $_POST['user_name'] ?? 'Guest';
    
    if (empty($roomId)) {
        echo json_encode(['success' => false, 'error' => 'Room ID required']);
        return;
    }
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    $userId = generateUserId();
    $room['users'][$userId] = [
        'id' => $userId,
        'name' => $userName,
        'role' => 'guest',
        'joined_at' => time(),
        'is_sharing' => false
    ];
    
    saveRoom($roomId, $room);
    
    echo json_encode([
        'success' => true,
        'room' => $room,
        'your_user_id' => $userId
    ]);
}

function leaveRoom() {
    $roomId = $_POST['room_id'] ?? '';
    $userId = $_POST['user_id'] ?? '';
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    if (isset($room['users'][$userId])) {
        unset($room['users'][$userId]);
        
        // If host leaves, end screen share
        if ($room['screen_share']['host_id'] === $userId) {
            $room['screen_share'] = [
                'active' => false,
                'host_id' => null,
                'offer' => null,
                'ice_candidates' => []
            ];
        }
        
        // Delete room if empty
        if (empty($room['users'])) {
            deleteRoom($roomId);
            echo json_encode(['success' => true, 'room_deleted' => true]);
            return;
        }
        
        saveRoom($roomId, $room);
    }
    
    echo json_encode(['success' => true]);
}

function getRoom() {
    $roomId = $_GET['room_id'] ?? '';
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    echo json_encode(['success' => true, 'room' => $room]);
}

function listRooms() {
    $rooms = [];
    $files = glob(ROOMS_DIR . '/*.json');
    
    foreach ($files as $file) {
        $room = json_decode(file_get_contents($file), true);
        if ($room) {
            $rooms[] = [
                'id' => $room['id'],
                'created_at' => $room['created_at'],
                'user_count' => count($room['users']),
                'screen_share_active' => $room['screen_share']['active']
            ];
        }
    }
    
    echo json_encode(['success' => true, 'rooms' => $rooms]);
}

// ==================== WEBRTC SIGNALING ====================

function handleSignal() {
    $roomId = $_POST['room_id'] ?? '';
    $userId = $_POST['user_id'] ?? '';
    $signal = $_POST['signal'] ?? '';
    $data = json_decode($_POST['data'] ?? '{}', true);
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    // Store signal
    $message = [
        'id' => uniqid(),
        'from' => $userId,
        'signal' => $signal,
        'data' => $data,
        'timestamp' => time()
    ];
    
    $room['messages'][] = $message;
    
    // Keep only last 100 messages
    if (count($room['messages']) > 100) {
        $room['messages'] = array_slice($room['messages'], -100);
    }
    
    saveRoom($roomId, $room);
    
    echo json_encode(['success' => true, 'message_id' => $message['id']]);
}

function getSignals() {
    $roomId = $_GET['room_id'] ?? '';
    $after = $_GET['after'] ?? '0';
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    $messages = [];
    foreach ($room['messages'] as $msg) {
        if ($msg['timestamp'] > $after) {
            $messages[] = $msg;
        }
    }
    
    echo json_encode(['success' => true, 'messages' => $messages]);
}

// ==================== SCREEN SHARE OFFER/ANSWER ====================

function updateOffer() {
    $roomId = $_POST['room_id'] ?? '';
    $userId = $_POST['user_id'] ?? '';
    $offer = json_decode($_POST['offer'] ?? '{}', true);
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    $room['screen_share'] = [
        'active' => true,
        'host_id' => $userId,
        'offer' => $offer,
        'ice_candidates' => [],
        'started_at' => time()
    ];
    
    if (isset($room['users'][$userId])) {
        $room['users'][$userId]['is_sharing'] = true;
    }
    
    saveRoom($roomId, $room);
    
    echo json_encode(['success' => true]);
}

function getOffer() {
    $roomId = $_GET['room_id'] ?? '';
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    echo json_encode([
        'success' => true,
        'active' => $room['screen_share']['active'],
        'offer' => $room['screen_share']['offer'],
        'host_id' => $room['screen_share']['host_id']
    ]);
}

function updateIceCandidates() {
    $roomId = $_POST['room_id'] ?? '';
    $userId = $_POST['user_id'] ?? '';
    $candidates = json_decode($_POST['candidates'] ?? '[]', true);
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    foreach ($candidates as $candidate) {
        $room['screen_share']['ice_candidates'][] = [
            'from' => $userId,
            'candidate' => $candidate,
            'timestamp' => time()
        ];
    }
    
    // Keep only last 100 candidates
    if (count($room['screen_share']['ice_candidates']) > 100) {
        $room['screen_share']['ice_candidates'] = array_slice(
            $room['screen_share']['ice_candidates'], -100
        );
    }
    
    saveRoom($roomId, $room);
    
    echo json_encode(['success' => true]);
}

function getIceCandidates() {
    $roomId = $_GET['room_id'] ?? '';
    $after = $_GET['after'] ?? '0';
    
    $room = loadRoom($roomId);
    if (!$room) {
        echo json_encode(['success' => false, 'error' => 'Room not found']);
        return;
    }
    
    $candidates = [];
    foreach ($room['screen_share']['ice_candidates'] as $ice) {
        if ($ice['timestamp'] > $after) {
            $candidates[] = $ice;
        }
    }
    
    echo json_encode(['success' => true, 'candidates' => $candidates]);
}

// ==================== MAINTENANCE ====================

function cleanupRooms() {
    $cleaned = 0;
    $files = glob(ROOMS_DIR . '/*.json');
    $now = time();
    
    foreach ($files as $file) {
        $room = json_decode(file_get_contents($file), true);
        if ($room && ($now - $room['created_at']) > ROOM_EXPIRY) {
            unlink($file);
            $cleaned++;
        }
    }
    
    echo json_encode(['success' => true, 'rooms_cleaned' => $cleaned]);
}

// ==================== HELPERS ====================

function generateRoomId() {
    return substr(md5(uniqid(rand(), true)), 0, 8);
}

function generateUserId() {
    return 'user_' . substr(md5(uniqid(rand(), true)), 0, 12);
}

function loadRoom($roomId) {
    $file = ROOMS_DIR . '/' . $roomId . '.json';
    if (!file_exists($file)) {
        return null;
    }
    return json_decode(file_get_contents($file), true);
}

function saveRoom($roomId, $room) {
    $file = ROOMS_DIR . '/' . $roomId . '.json';
    file_put_contents($file, json_encode($room, JSON_PRETTY_PRINT));
}

function deleteRoom($roomId) {
    $file = ROOMS_DIR . '/' . $roomId . '.json';
    if (file_exists($file)) {
        unlink($file);
    }
}
